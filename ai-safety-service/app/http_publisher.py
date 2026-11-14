"""
告警 HTTP 推送器（对接真实 Spring Boot 后端）。

职责：
    - 将告警按后端 AlarmCreateDTO 契约推送（POST /api/alarms）；
    - 成功判定：HTTP 200 且响应体 code == 0（data.id 为新建告警 ID）；
    - 后端不可达（健康探测失败）或推送失败时落盘到 ./pending_alerts/{event_id}.json；
    - 后台线程每 30 秒尝试重新发送本地堆积的告警；
    - 后端开启鉴权（auth.enabled=true）时，自动调用 /api/auth/login 获取
      Bearer Token 并附加 Authorization 头；收到 401 时重新登录后重试一次。

类型码映射（后端 alarm.type.ai 字典）：
    NO-Hardhat      -> NO_HELMET  未佩戴安全帽
    NO-Safety Vest  -> NO_VEST    未穿安全服
    smoking         -> SMOKING    现场吸烟
    fire            -> FIRE       现场明火

告警等级映射（alarmLevel）：confidence>=0.8 -> 3（控制）；>=0.6 -> 2（警报）；
否则 -> 1（预警）。

线程安全：每路摄像头一个实例，其后台 flush 线程与主线程并发访问
pending 目录，因此目录扫描与写入均以锁保护。
"""

from __future__ import annotations

import json
import logging
import re
import threading
import time
from pathlib import Path

import requests

from app.config import settings
from app.health_check import JavaHealthChecker

logger = logging.getLogger("ai_monitor.publisher")

# 后端 alarm.type.ai 字典编码
TYPE_CODE_MAP = {
    "NO-Hardhat": "NO_HELMET",
    "NO-Safety Vest": "NO_VEST",
    "smoking": "SMOKING",
    "fire": "FIRE",
}
# 类型码中文描述（用于 alarmContent）
TYPE_CODE_DESC = {
    "NO_HELMET": "未佩戴安全帽",
    "NO_VEST": "未穿安全服",
    "SMOKING": "现场吸烟",
    "FIRE": "现场明火",
}
# 严重度 -> 后端告警等级（1 预警 / 2 警报 / 3 控制）
LEVEL_OF_SEVERITY = {"low": 1, "medium": 2, "high": 3}


class HttpPublisher:
    """告警推送器：按 AlarmCreateDTO 契约 POST + 失败落盘缓冲 + 周期重试。"""

    def __init__(
        self,
        java_endpoint: str,
        camera_id: str,
        camera_name: str,
        location_name: str,
        screenshot_base_url: str = "",
        pending_dir: str | Path | None = None,
        retry_interval: float | None = None,
        timeout: float | None = None,
        health_check_timeout: float | None = None,
        # ---- 后端 AlarmCreateDTO 对接（真实 Spring Boot）----
        project_id: int | None = None,
        backend_camera_id: int | None = None,
        device_id: int | None = None,
        monitor_point_id: int | None = None,
        threshold_range: str = "",
        backend_auth_enabled: bool = False,
        backend_username: str = "",
        backend_password: str = "",
        login_endpoint: str = "",
        screenshot_endpoint: str = "",
    ) -> None:
        """
        Args:
            java_endpoint:        后端告警接收地址，如 http://host:8080/api/alarms。
            camera_id:            摄像头 ID（AI 侧）。
            camera_name:          摄像头名称。
            location_name:        点位名称。
            screenshot_base_url:  截图 HTTP 基础 URL（用于拼截图外链）。
            pending_dir:          失败缓冲目录（默认 settings.pending_alert_dir）。
            retry_interval:       堆积告警重试间隔秒（默认 30）。
            timeout:              POST 推送超时秒（默认 5）。
            health_check_timeout: 健康探测超时秒（默认 2）。
            project_id:           后端项目主键 projectId（AlarmCreateDTO 必填）。
            backend_camera_id:    后端摄像头主键 cameraId（Long），可选。
            device_id:            后端设备主键 deviceId，可选。
            monitor_point_id:     后端监测点位主键 monitorPointId，可选。
            threshold_range:      阈值范围描述，如 "conf>=0.25"，可选。
            backend_auth_enabled: 后端是否开启鉴权（auth.enabled=true）。
            backend_username:     后端登录用户名（鉴权开启时）。
            backend_password:     后端登录密码（鉴权开启时）。
            login_endpoint:       后端登录接口，缺省由 java_endpoint 推导。
            screenshot_endpoint:  截图关联接口模板，含 {alarm_id} 占位符，
                                  如 http://host:8080/api/ai/alarms/{alarm_id}/screenshot。
                                  为空时不关联截图。
        """
        self.java_endpoint = java_endpoint
        self.camera_id = camera_id
        self.camera_name = camera_name
        self.location_name = location_name
        self.screenshot_base_url = (screenshot_base_url or "").rstrip("/")

        # ---- 后端 AlarmCreateDTO 字段 ----
        self.project_id: int | None = int(project_id) if project_id else None
        self.backend_camera_id: int | None = int(backend_camera_id) if backend_camera_id else None
        self.device_id: int | None = int(device_id) if device_id else None
        self.monitor_point_id: int | None = int(monitor_point_id) if monitor_point_id else None
        self.threshold_range: str = str(threshold_range or "")
        self.screenshot_endpoint: str = (screenshot_endpoint or "").strip()

        self.pending_dir = Path(pending_dir or settings.pending_alert_dir)
        self.pending_dir.mkdir(parents=True, exist_ok=True)

        self.retry_interval = float(retry_interval or settings.retry_interval)
        self.timeout = float(timeout or settings.http_timeout)
        self._health = JavaHealthChecker(
            java_endpoint,
            login_url=login_endpoint,
            username=backend_username,
            password=backend_password,
            auth_enabled=backend_auth_enabled,
            timeout=float(health_check_timeout or settings.health_check_timeout),
        )

        self._session = requests.Session()
        self._dir_lock = threading.Lock()  # 保护 pending 目录读写
        self._stop_event = threading.Event()

        # 后台重试线程：每 30 秒扫描 pending 目录重发
        self._flush_thread = threading.Thread(
            target=self._flush_loop, name=f"publisher-flush-{camera_id}", daemon=True
        )
        self._flush_thread.start()

        if not self.project_id:
            logger.warning(
                "HttpPublisher 未配置 project_id，后端 AlarmCreateDTO 校验将失败: camera=%s",
                camera_id,
            )
        logger.info(
            "HttpPublisher 初始化: camera=%s, endpoint=%s, project_id=%s, "
            "cameraId(后端)=%s, auth=%s, pending=%s",
            camera_id,
            java_endpoint,
            self.project_id,
            self.backend_camera_id,
            backend_auth_enabled,
            self.pending_dir,
        )

    # ------------------------------------------------------------------ #
    # 告警构建
    # ------------------------------------------------------------------ #
    @classmethod
    def severity_of(cls, confidence: float) -> str:
        """置信度 -> 严重度（high/medium/low）。"""
        if confidence >= 0.8:
            return "high"
        if confidence >= 0.6:
            return "medium"
        return "low"

    @classmethod
    def type_code_of(cls, violation_type: str) -> str:
        """模型类别名 -> 后端 AI 告警类型码；无法识别时原样大写（截断 30）。"""
        if not violation_type:
            return "UNKNOWN"
        target = cls._normalize(violation_type)
        for key, code in TYPE_CODE_MAP.items():
            if cls._normalize(key) == target:
                return code
        return violation_type.upper()[:30]

    @staticmethod
    def _normalize(name: str) -> str:
        """类别名归一化：仅保留字母数字并转小写。"""
        return re.sub(r"[^a-z0-9]", "", name.lower())

    def _alarm_content(self, type_code: str, confidence: float, screenshot_url: str) -> str:
        desc = TYPE_CODE_DESC.get(type_code, type_code)
        camera = self.camera_name or self.camera_id
        content = f"摄像头[{camera}]检测到{desc}({type_code})，置信度{confidence:.3f}"
        if screenshot_url:
            content += f"。截图：{screenshot_url}"
        return content[:500]  # 后端 alarmContent 上限 500

    def build_alert(
        self,
        event_id: str,
        violation_type: str,
        confidence: float,
        screenshot_filename: str,
        bbox: dict,
        timestamp: str | None = None,
    ) -> dict:
        """
        构建告警 dict（AlarmCreateDTO 字段 + _ 前缀内部元信息）。

        返回的 dict 可直接 POST /api/alarms（publish_alert 内部会剥离
        _ 前缀元信息）。_event_id/_severity/_screenshot_url 等供日志与
        堆积重放使用。
        """
        from datetime import datetime

        ts = timestamp or datetime.now().isoformat()
        alarm_time = ts.split(".")[0]  # 去掉微秒，兼容后端 LocalDateTime 解析

        type_code = self.type_code_of(violation_type)
        severity = self.severity_of(confidence)
        level = LEVEL_OF_SEVERITY.get(severity, 1)
        url = (
            f"{self.screenshot_base_url}/{screenshot_filename}"
            if self.screenshot_base_url
            else screenshot_filename
        )

        alert = {
            # ---- 后端 AlarmCreateDTO ----
            "projectId": self.project_id,
            "sourceType": 3,  # 3 = AI
            "alarmLevel": level,
            "alarmTypeCode": type_code,
            "alarmContent": self._alarm_content(type_code, confidence, url),
            "currentValue": round(float(confidence), 4),
            "thresholdRange": self.threshold_range or None,
            "deviceId": self.device_id,
            "monitorPointId": self.monitor_point_id,
            "cameraId": self.backend_camera_id,
            "relatedAlarmId": None,
            "alarmTime": alarm_time,
            # ---- 内部元信息（_ 前缀，POST 前剥离）----
            "_event_id": event_id,
            "_violation_type": violation_type,
            "_type_code": type_code,
            "_ai_label": TYPE_CODE_DESC.get(type_code, type_code),
            "_confidence": round(float(confidence), 4),
            "_severity": severity,
            "_screenshot_filename": screenshot_filename,
            "_screenshot_url": url,
            "_bbox": bbox,
        }
        return alert

    @staticmethod
    def _to_payload(alert: dict) -> dict:
        """剥离 _ 前缀内部元信息，得到可直传后端的 AlarmCreateDTO body。"""
        return {k: v for k, v in alert.items() if not k.startswith("_")}

    # ------------------------------------------------------------------ #
    # 推送
    # ------------------------------------------------------------------ #
    def _post(self, payload: dict, retry_auth: bool = True) -> tuple[bool, int, dict | None, str]:
        """
        POST 推送；返回 (成功, HTTP状态码, 响应JSON, 说明)。

        401 且开启鉴权时：丢弃 token、重新登录后重试一次。
        """
        try:
            resp = self._session.post(
                self.java_endpoint,
                json=payload,
                headers=self._health.auth_headers,
                timeout=self.timeout,
            )
        except requests.RequestException as exc:
            return False, 0, None, f"网络异常: {exc}"
        except Exception as exc:  # 兜底
            return False, 0, None, f"未预期异常: {exc}"

        if resp.status_code == 401 and retry_auth and self._health.auth_enabled:
            logger.warning("后端返回 401（token 失效），重新登录后重试一次")
            self._health.invalidate_token()
            self._health.ensure_token(force=True)
            return self._post(payload, retry_auth=False)

        body = None
        try:
            body = resp.json()
        except Exception:
            body = None

        if resp.status_code == 200 and isinstance(body, dict) and body.get("code") == 0:
            return True, resp.status_code, body, "ok"

        msg = f"HTTP {resp.status_code}"
        if isinstance(body, dict) and body.get("msg"):
            msg += f": {body['msg']}"
        return False, resp.status_code, body, msg

    # ------------------------------------------------------------------ #
    # 截图关联（POST /api/ai/alarms/{id}/screenshot）
    # ------------------------------------------------------------------ #
    def _link_screenshot(
        self,
        alarm_id: int | None,
        screenshot_url: str,
        ai_label: str | None = None,
        ai_confidence: float | None = None,
    ) -> bool:
        """
        告警推送成功后，将截图地址关联到后端告警记录（幂等），同时携带
        AI 识别标签与置信度，供视频监控模块展示。

        未配置 screenshot_endpoint 或地址为空时静默跳过；关联失败仅记
        日志（截图 URL 已内嵌在 alarmContent 文本中兜底），不阻塞告警。
        """
        if not self.screenshot_endpoint or not alarm_id or not screenshot_url:
            return False
        endpoint = self.screenshot_endpoint.replace("{alarm_id}", str(alarm_id))
        body: dict = {"screenshotUrl": screenshot_url}
        if ai_label is not None:
            body["aiLabel"] = str(ai_label)[:50]
        if ai_confidence is not None:
            try:
                body["aiConfidence"] = round(float(ai_confidence), 4)
            except (TypeError, ValueError):
                pass
        try:
            resp = self._session.post(
                endpoint,
                json=body,
                headers=self._health.auth_headers,
                timeout=self.timeout,
            )
            if resp.status_code == 401 and self._health.auth_enabled:
                logger.warning("截图关联返回 401（token 失效），重新登录后重试一次")
                self._health.invalidate_token()
                self._health.ensure_token(force=True)
                resp = self._session.post(
                    endpoint,
                    json=body,
                    headers=self._health.auth_headers,
                    timeout=self.timeout,
                )
            if resp.status_code == 200:
                body = None
                try:
                    body = resp.json()
                except Exception:
                    body = None
                if isinstance(body, dict) and body.get("code") == 0:
                    logger.info(
                        "告警截图已关联: alarm_id=%s url=%s",
                        alarm_id,
                        screenshot_url,
                    )
                    return True
                msg = body.get("msg") if isinstance(body, dict) else ""
                logger.warning("告警截图关联失败（HTTP 200 但 code!=0%s）: alarm_id=%s", f": {msg}" if msg else "", alarm_id)
                return False
            logger.warning("告警截图关联失败（HTTP %s）: alarm_id=%s", resp.status_code, alarm_id)
        except requests.RequestException as exc:
            logger.warning("告警截图关联网络异常: alarm_id=%s err=%s", alarm_id, exc)
        return False

    def publish_alert(self, alert: dict) -> bool:
        """推送单条告警；失败落盘缓冲。返回是否推送成功。"""
        if not self._health.is_healthy():
            logger.warning("后端不可达，告警落盘: event=%s", alert.get("_event_id"))
            self._save_pending(alert)
            return False

        payload = self._to_payload(alert)
        ok, status, body, msg = self._post(payload)
        if ok:
            alarm_id = None
            data = (body or {}).get("data") if isinstance(body, dict) else None
            if isinstance(data, dict):
                alarm_id = data.get("id")
            logger.info(
                "告警推送成功: event=%s type=%s conf=%.3f alarm_id=%s",
                alert.get("_event_id"),
                alert.get("_violation_type"),
                alert.get("_confidence", 0),
                alarm_id,
            )
            self._link_screenshot(
                alarm_id,
                alert.get("_screenshot_url"),
                ai_label=alert.get("_ai_label"),
                ai_confidence=alert.get("_confidence"),
            )
            return True

        logger.warning(
            "告警推送失败（%s），落盘缓冲: event=%s type=%s",
            msg,
            alert.get("_event_id"),
            alert.get("_violation_type"),
        )
        self._save_pending(alert)
        return False

    # ------------------------------------------------------------------ #
    # 失败落盘与周期重发
    # ------------------------------------------------------------------ #
    def _save_pending(self, alert: dict) -> None:
        """推送失败/后端不可达时，将告警（含元信息）落盘待重发。"""
        event_id = alert.get("_event_id", f"unknown-{int(time.time() * 1000)}")
        path = self.pending_dir / f"{event_id}.json"
        try:
            with self._dir_lock:
                path.write_text(
                    json.dumps(alert, ensure_ascii=False, indent=2), encoding="utf-8"
                )
            logger.info("告警已落盘缓冲: %s", path)
        except Exception as exc:
            logger.error("告警落盘失败 %s: %s", path, exc)

    def flush_pending(self) -> int:
        """
        重发本地堆积告警，成功即删除对应文件。返回成功条数。
        """
        with self._dir_lock:
            files = sorted(self.pending_dir.glob("*.json"))
        if not files:
            return 0
        if not self._health.is_healthy():
            logger.info("后端仍不可达，跳过堆积告警重发（%d 条）", len(files))
            return 0

        sent = 0
        for path in files:
            try:
                alert = json.loads(path.read_text(encoding="utf-8"))
            except Exception:
                logger.warning("忽略损坏的缓冲文件: %s", path)
                continue
            payload = self._to_payload(alert)
            ok, status, body, msg = self._post(payload)
            if ok:
                alarm_id = None
                data = (body or {}).get("data") if isinstance(body, dict) else None
                if isinstance(data, dict):
                    alarm_id = data.get("id")
                self._link_screenshot(
                    alarm_id,
                    alert.get("_screenshot_url"),
                    ai_label=alert.get("_ai_label"),
                    ai_confidence=alert.get("_confidence"),
                )
                with self._dir_lock:
                    try:
                        path.unlink(missing_ok=True)
                    except Exception:
                        pass
                sent += 1
                logger.info("堆积告警重发成功并移除: %s", path.name)
            else:
                logger.warning("堆积告警重发失败（%s）: %s", msg, path.name)
        return sent

    def _flush_loop(self) -> None:
        """后台线程：周期重发堆积告警。"""
        while not self._stop_event.wait(self.retry_interval):
            try:
                self.flush_pending()
            except Exception:
                logger.exception("堆积告警重发循环异常")

    def stop(self) -> None:
        """停止后台重试线程并释放资源。"""
        self._stop_event.set()
        if self._flush_thread.is_alive():
            self._flush_thread.join(timeout=3)
        try:
            self._health.close()
            self._session.close()
        except Exception:
            pass
