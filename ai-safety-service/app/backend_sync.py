"""
后端摄像头同步器（BackendCameraSyncer）。

定时从 Java 后端拉取摄像头列表（GET /video/cameras，context-path=/api），
自动启动/停止本地监测线程，使 AI 服务与后端注册的摄像头保持一致，
替代手工维护 configs/*.yaml（实现"AI 从后端获取视频流"环节）。

工作方式：
    - 启动时立即同步一次，之后按 interval 周期同步；
    - 后端返回有 streamUrl 的摄像头 -> 本地启动一路监测（camera_id=BE-{id}）；
    - 已同步过的摄像头在后端消失（被删除/停用）-> 停止本地监测；
    - 若 configs/*.yaml 已启动一路且 backend_camera_id 与后端 id 相同，
      则跳过重复启动（以 yaml 实例为准，登记但不再起第二路）；
    - 后端返回空列表时跳过本次同步，避免误删本地监测。

配置（app/config.py，环境变量 AI_BACKEND_SYNC_*）：
    AI_BACKEND_SYNC_ENABLED         同步开关（默认 false）
    AI_BACKEND_SYNC_BASE_URL        后端 base URL（含 context-path），
                                    如 http://127.0.0.1:8080/api
    AI_BACKEND_SYNC_INTERVAL        同步周期秒（默认 60）
    AI_BACKEND_SYNC_CONF/IOU/DETECT_INTERVAL/COOLDOWN/CLASSES
                                    检测默认参数（后端不感知这些 AI 参数）
    AI_BACKEND_SYNC_MODEL_PATH      可选：覆盖全局模型路径
    AI_BACKEND_SYNC_SCREENSHOT_HOST 截图外链使用的 AI 服务地址（默认 127.0.0.1）
    AI_BACKEND_SYNC_LOGIN_ENDPOINT  登录接口覆盖（默认 {base}/auth/login）

鉴权：复用后端登录配置（backend_auth_enabled/username/password）；收到
401 时自动重新登录并重试一次。
"""

from __future__ import annotations

import logging
import threading

import requests

from app.camera_manager import CameraManager
from app.config import settings

logger = logging.getLogger("ai_monitor.backend_sync")

# 同步器启动的本地摄像头 ID 前缀（与 yaml 启动的区分，避免误停）
_CAMERA_ID_PREFIX = "BE-"


class BackendCameraSyncer:
    """从 Java 后端拉取摄像头列表并同步本地监测线程。"""

    def __init__(self, manager: CameraManager, interval: float | None = None) -> None:
        self._manager = manager
        self._settings = settings
        self._interval = float(interval or settings.backend_sync_interval)
        self._base_url = settings.backend_sync_base_url
        self._session = requests.Session()
        self._token: str | None = None
        self._stop_event = threading.Event()
        self._thread: threading.Thread | None = None
        # backend_id -> 本地 camera_id；None 表示该后端摄像头由 yaml 实例覆盖
        self._synced: dict[int, str | None] = {}

    # ------------------------------------------------------------------ #
    # 生命周期
    # ------------------------------------------------------------------ #
    def start(self) -> None:
        """启动后台同步线程（先立即同步一次，再按周期循环）。"""
        if self._thread and self._thread.is_alive():
            return
        if not self._base_url:
            logger.error("后端摄像头同步器无法启动：未配置 AI_BACKEND_SYNC_BASE_URL")
            return
        self._stop_event.clear()
        self._thread = threading.Thread(
            target=self._run, name="backend-camera-sync", daemon=True
        )
        self._thread.start()
        logger.info(
            "后端摄像头同步器已启动: base=%s interval=%ss",
            self._base_url,
            self._interval,
        )

    def stop(self) -> None:
        """停止同步线程并释放连接。"""
        self._stop_event.set()
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=5)
        try:
            self._session.close()
        except Exception:
            pass
        logger.info("后端摄像头同步器已停止")

    def _run(self) -> None:
        self.sync_once()
        while not self._stop_event.wait(self._interval):
            self.sync_once()

    # ------------------------------------------------------------------ #
    # 拉取后端摄像头列表
    # ------------------------------------------------------------------ #
    def _auth_headers(self) -> dict:
        headers = {"Accept": "application/json"}
        if self._token:
            headers["Authorization"] = f"Bearer {self._token}"
        return headers

    def _refresh_token(self) -> None:
        """登录后端获取 Bearer Token；未开启鉴权时跳过。"""
        s = self._settings
        if not s.backend_auth_enabled:
            return
        login_url = s.backend_sync_login_endpoint or f"{self._base_url}/auth/login"
        resp = self._session.post(
            login_url,
            json={"username": s.backend_username, "password": s.backend_password},
            timeout=s.backend_sync_timeout,
        )
        resp.raise_for_status()
        body = resp.json()
        if body.get("code") != 0:
            raise RuntimeError(f"后端登录失败: {body.get('msg')}")
        data = body.get("data") or {}
        token = (
            data.get("token")
            or data.get("accessToken")
            or data.get("access_token")
            or body.get("token")
        )
        if not token:
            raise RuntimeError("后端登录响应中未找到 token")
        self._token = str(token)
        logger.info("后端摄像头同步器登录成功")

    def _fetch_all_cameras(self) -> list[dict]:
        """翻页拉取后端全部摄像头（VideoCameraVO 子集）。"""
        page, size = 1, 100
        rows: list[dict] = []
        while True:
            url = f"{self._base_url}/video/cameras"
            try:
                resp = self._session.get(
                    url,
                    params={"pageNum": page, "pageSize": size},
                    headers=self._auth_headers(),
                    timeout=self._settings.backend_sync_timeout,
                )
            except requests.RequestException as exc:
                raise RuntimeError(f"请求失败: {exc}") from exc

            if resp.status_code == 401:
                logger.warning("后端摄像头列表返回 401（token 失效），重新登录后重试一次")
                self._refresh_token()
                try:
                    resp = self._session.get(
                        url,
                        params={"pageNum": page, "pageSize": size},
                        headers=self._auth_headers(),
                        timeout=self._settings.backend_sync_timeout,
                    )
                except requests.RequestException as exc:
                    raise RuntimeError(f"请求失败: {exc}") from exc

            resp.raise_for_status()
            body = resp.json()
            if body.get("code") != 0:
                raise RuntimeError(
                    f"后端返回错误: code={body.get('code')} msg={body.get('msg')}"
                )
            data = body.get("data") or {}
            records = data.get("records") or []
            rows.extend(records)
            total = int(data.get("total") or 0)
            pages = int(data.get("pages") or 0)
            if page >= pages or not records or len(rows) >= total:
                break
            page += 1
        return rows

    # ------------------------------------------------------------------ #
    # 配置构建与差异计算
    # ------------------------------------------------------------------ #
    @staticmethod
    def _make_local_id(backend_id: int) -> str:
        return f"{_CAMERA_ID_PREFIX}{backend_id}"

    @staticmethod
    def _safe_int(value) -> int | None:
        """后端 deviceId 为字符串时安全转换（AlarmCreateDTO 要求 Long）。"""
        if value is None or value == "":
            return None
        try:
            return int(value)
        except (TypeError, ValueError):
            return None

    def _existing_local_for_backend(self, backend_id: int) -> str | None:
        """返回已在运行且 backend_camera_id 匹配的本地 camera_id（yaml 启动的）。"""
        for st in self._manager.get_status():
            cam_id = st.get("camera_id")
            if not cam_id or cam_id.startswith(_CAMERA_ID_PREFIX):
                continue
            mon = self._manager.get_monitor(cam_id)
            if mon is not None and int(mon.backend_camera_id or 0) == int(backend_id):
                return cam_id
        return None

    def _build_config(self, cam: dict, local_id: str) -> dict | None:
        """将后端 VideoCameraVO 转换为本地摄像头配置；无流地址返回 None。"""
        stream_url = (cam.get("streamUrl") or "").strip()
        if not stream_url:
            logger.info("后端摄像头 id=%s 无 streamUrl，跳过同步启动", cam.get("id"))
            return None
        s = self._settings
        backend_id = int(cam["id"])
        name = cam.get("cameraName") or cam.get("name") or f"后端摄像头{backend_id}"
        location = cam.get("locationName") or cam.get("location") or ""
        screenshot_dir = f"BE-{backend_id}"
        screenshot_base = (
            f"http://{s.backend_sync_screenshot_host}:{s.port}"
            f"/screenshots/{screenshot_dir}"
        )
        return {
            "camera_id": local_id,
            "camera_name": name,
            "rtsp_url": stream_url,
            "location_name": location,
            "project_id": s.backend_project_id,
            "backend_camera_id": backend_id,
            "device_id": self._safe_int(cam.get("deviceId")),
            "monitor_point_id": s.backend_monitor_point_id,
            "threshold_range": f"conf>={s.backend_sync_conf:.2f}",
            "conf_threshold": s.backend_sync_conf,
            "iou_threshold": s.backend_sync_iou,
            "detect_interval": s.backend_sync_detect_interval,
            "cooldown_seconds": s.backend_sync_cooldown,
            "enabled_classes": list(s.backend_sync_classes),
            "screenshot_dir": screenshot_dir,
            "screenshot_base_url": screenshot_base,
            "java_endpoint": f"{self._base_url}/alarms",
            "screenshot_endpoint": (
                f"{self._base_url}/ai/alarms/{{alarm_id}}/screenshot"
            ),
            "backend_auth_enabled": s.backend_auth_enabled,
            "backend_username": s.backend_username,
            "backend_password": s.backend_password,
            "login_endpoint": s.backend_sync_login_endpoint or f"{self._base_url}/auth/login",
        }

    # ------------------------------------------------------------------ #
    # 同步主流程
    # ------------------------------------------------------------------ #
    def sync_once(self) -> dict:
        """
        执行一次全量同步，返回 {"added": [...], "removed": [...]}。

        异常（后端不可达/返回错误）不抛出，记日志后返回空结果，保证
        定时循环稳定运行；后端空列表时跳过，避免误删本地监测。
        """
        result = {"added": [], "removed": []}
        try:
            cameras = self._fetch_all_cameras()
        except Exception as exc:
            logger.error("拉取后端摄像头列表失败: %s", exc)
            return result
        if not cameras:
            logger.warning("后端摄像头列表为空，跳过本次同步（避免误删本地监测）")
            return result

        current: dict[int, dict] = {}
        for cam in cameras:
            try:
                cid = int(cam.get("id"))
            except (TypeError, ValueError):
                continue
            if cam.get("streamUrl"):
                current[cid] = cam

        # 移除：后端已不存在的摄像头
        for backend_id in list(self._synced.keys()):
            if backend_id in current:
                continue
            local_id = self._synced[backend_id]
            if local_id and self._manager.stop_camera(local_id):
                result["removed"].append(local_id)
            del self._synced[backend_id]
            logger.info(
                "后端摄像头 id=%s 已移除，本地监测 %s 已停止",
                backend_id,
                local_id or "（yaml 实例，不干预）",
            )

        # 新增/登记
        for backend_id, cam in current.items():
            if backend_id in self._synced:
                continue
            existing = self._existing_local_for_backend(backend_id)
            if existing:
                self._synced[backend_id] = None
                logger.info(
                    "后端摄像头 id=%s 已有 yaml 监测实例 %s，跳过同步启动",
                    backend_id,
                    existing,
                )
                continue
            local_id = self._make_local_id(backend_id)
            config = self._build_config(cam, local_id)
            if config is None:
                continue
            try:
                self._manager.start_camera(config)
            except Exception as exc:
                logger.error("后端摄像头 id=%s 同步启动失败: %s", backend_id, exc)
                continue
            self._synced[backend_id] = local_id
            result["added"].append(local_id)
            logger.info(
                "已同步启动后端摄像头 id=%s: %s -> %s（名称=%s）",
                backend_id,
                cam.get("streamUrl"),
                local_id,
                cam.get("cameraName") or cam.get("name") or "",
            )

        return result
