"""
单路摄像头监测线程（核心循环）。

职责：
    - 打开 RTSP/RTMP/本地视频文件，cv2.CAP_FFMPEG 后端 + BUFFERSIZE=1 降延迟；
    - 断流后 3 秒重试、最多 10 次后进入 error 状态（报警）；
    - 跳帧检测（detect_interval），检测、ROI 过滤、类别过滤、防抖；
    - 命中违规 -> 截图（全图 + bbox 裁剪）-> 推送 Java -> 记录回放。

所有异常都被兜底捕获，保证视频断开 / 推理异常 / 推送失败都不会导致线程崩溃。
"""

from __future__ import annotations

import logging
import threading
import time
from collections import deque
from datetime import datetime
from pathlib import Path

import cv2

from app.config import settings
from app.detector import YOLODetector
from app.http_publisher import HttpPublisher
from app.utils import screenshot as shot
from app.utils.debounce import Debouncer
from app.utils.frame_buffer import FrameBuffer
from app.utils.roi_filter import ROIFilter

logger = logging.getLogger("ai_monitor.monitor")


class CameraMonitor(threading.Thread):
    """一路摄像头的独立监测线程。"""

    def __init__(self, config: dict) -> None:
        """
        Args:
            config: 摄像头配置字典（来自 configs/*.yaml 或 /api/cameras/start）。
        """
        super().__init__(name=f"camera-{config.get('camera_id', 'unknown')}", daemon=True)

        # ---- 基础信息 ----
        self.camera_id: str = config["camera_id"]
        self.camera_name: str = config.get("camera_name", "")
        self.location_name: str = config.get("location_name", "")
        self.rtsp_url: str = config["rtsp_url"]

        # ---- 检测参数 ----
        self.model_path: str | None = config.get("model_path") or str(settings.model_path)
        self.detect_interval: int = max(1, int(config.get("detect_interval", 3)))
        self.conf_threshold: float = float(config.get("conf_threshold", 0.25))
        self.iou_threshold: float = float(config.get("iou_threshold", 0.45))
        self.cooldown_seconds: float = float(config.get("cooldown_seconds", 15))
        self.debounce_mode: str = str(config.get("debounce_mode", "grid"))
        self.debounce_grid_x: int = int(config.get("debounce_grid_x", 50))
        self.debounce_grid_y: int = int(config.get("debounce_grid_y", 50))
        self.enabled_classes: list[str] = config.get("enabled_classes") or []

        # ---- 截图与推送 ----
        self.screenshot_dir: str | None = config.get("screenshot_dir")
        self.screenshot_base_url: str = config.get("screenshot_base_url", "")
        self.java_endpoint: str = config.get("java_endpoint", "")

        # ---- 后端对接（真实 Spring Boot AlarmCreateDTO）----
        self.project_id: int | None = config.get("project_id") or settings.backend_project_id
        self.backend_camera_id: int | None = (
            config.get("backend_camera_id") or settings.backend_camera_id
        )
        self.device_id: int | None = config.get("device_id") or settings.backend_device_id
        self.monitor_point_id: int | None = (
            config.get("monitor_point_id") or settings.backend_monitor_point_id
        )
        self.threshold_range: str = config.get("threshold_range", "")
        self.backend_auth_enabled: bool = bool(
            config.get("backend_auth_enabled", settings.backend_auth_enabled)
        )
        self.backend_username: str = config.get("backend_username") or settings.backend_username
        self.backend_password: str = config.get("backend_password") or settings.backend_password
        self.login_endpoint: str = config.get("login_endpoint", "")
        self.screenshot_endpoint: str = (
            config.get("screenshot_endpoint") or settings.backend_screenshot_endpoint
        )

        # ---- 重连 ----
        self.max_reconnect: int = int(
            config.get("max_reconnect", settings.max_reconnect)
        )
        self.reconnect_delay: float = float(
            config.get("reconnect_delay", settings.reconnect_delay)
        )

        # ---- 运行时状态 ----
        self._capture: cv2.VideoCapture | None = None
        self._stop_event = threading.Event()
        self._frame_buffer = FrameBuffer(maxlen=32)
        self._debouncer = Debouncer(
            cooldown_seconds=self.cooldown_seconds,
            mode=self.debounce_mode,
            grid_x=self.debounce_grid_x,
            grid_y=self.debounce_grid_y,
        )
        self._roi = ROIFilter(config.get("roi_polygon"))
        self._replay: deque = deque(maxlen=settings.replay_size)
        self._frame_counter: int = 0

        # 状态字段（API 读取，加锁保护）
        self._status_lock = threading.Lock()
        self.state: str = "stopped"  # stopped/running/connecting/error
        self.fps: float = 0.0
        self.alert_count: int = 0
        self.last_alert_time: str | None = None
        self.last_error: str | None = None
        self._fps_ema: float = 0.0
        self._last_frame_time: float = time.monotonic()

        # 检测器（单例共享）与推送器
        self._detector = YOLODetector.get_instance(self.model_path)
        self._publisher = HttpPublisher(
            java_endpoint=self.java_endpoint,
            camera_id=self.camera_id,
            camera_name=self.camera_name,
            location_name=self.location_name,
            screenshot_base_url=self.screenshot_base_url,
            project_id=self.project_id,
            backend_camera_id=self.backend_camera_id,
            device_id=self.device_id,
            monitor_point_id=self.monitor_point_id,
            threshold_range=self.threshold_range,
            backend_auth_enabled=self.backend_auth_enabled,
            backend_username=self.backend_username,
            backend_password=self.backend_password,
            login_endpoint=self.login_endpoint,
            screenshot_endpoint=self.screenshot_endpoint,
        )
        if not self.java_endpoint:
            logger.warning(
                "摄像头 %s 未配置 java_endpoint，告警将仅落盘到 pending_alerts/", self.camera_id
            )

        logger.info(
            "CameraMonitor 初始化: %s (%s) url=%s interval=%d conf=%.2f classes=%s",
            self.camera_id,
            self.camera_name,
            self.rtsp_url,
            self.detect_interval,
            self.conf_threshold,
            self.enabled_classes or "ALL",
        )

    # ------------------------------------------------------------------ #
    # 主循环
    # ------------------------------------------------------------------ #
    def run(self) -> None:
        """线程入口：连接 -> 循环读取/检测 -> 清理。"""
        logger.info("摄像头线程启动: %s", self.camera_id)
        while not self._stop_event.is_set():
            # 无连接时尝试建立（内含 3 秒间隔、max_reconnect 次重试）
            if self._capture is None:
                self._connect()
                if self._capture is None:
                    # 连接失败：等待一小段时间后进入下一轮重试
                    self._stop_event.wait(10)
                    continue

            try:
                ret, frame = self._capture.read()
            except Exception as exc:
                logger.error("摄像头 %s 读取异常: %s", self.camera_id, exc)
                self._handle_stream_loss()
                continue

            if not ret or frame is None:
                self._handle_stream_loss()
                continue

            # 正常帧：FPS 统计 + 缓冲 + 跳帧检测
            self._update_fps()
            self._frame_buffer.put(frame)
            self._frame_counter += 1
            if self._frame_counter % self.detect_interval == 0:
                self._process_frame(frame)

        self._cleanup()
        logger.info("摄像头线程退出: %s", self.camera_id)

    # ------------------------------------------------------------------ #
    # 视频流连接 / 断开
    # ------------------------------------------------------------------ #
    def _open_capture(self) -> cv2.VideoCapture | None:
        """用 FFMPEG 后端打开视频流，并设置低缓冲。"""
        try:
            cap = cv2.VideoCapture(self.rtsp_url, cv2.CAP_FFMPEG)
            if not cap.isOpened():
                cap.release()
                return None
            # 降低内部缓冲，减少延迟（部分后端不支持时静默忽略）
            try:
                cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
            except Exception:
                pass
            return cap
        except Exception as exc:
            logger.error("打开视频流异常 %s: %s", self.rtsp_url, exc)
            return None

    def _connect(self) -> None:
        """
        建立连接：断开后按 reconnect_delay 秒重试，最多 max_reconnect 次；
        全部失败进入 error 状态并记录错误（报警），由主循环继续轮询重试。
        """
        with self._status_lock:
            self.state = "connecting"
        for attempt in range(1, self.max_reconnect + 1):
            if self._stop_event.is_set():
                return
            cap = self._open_capture()
            if cap is not None:
                self._capture = cap
                with self._status_lock:
                    self.state = "running"
                    self.last_error = None
                logger.info(
                    "摄像头 %s 连接成功（第 %d 次尝试）: %s",
                    self.camera_id, attempt, self.rtsp_url,
                )
                self._debouncer.clear()
                return
            logger.warning(
                "摄像头 %s 连接失败 %d/%d，%.1f 秒后重试: %s",
                self.camera_id, attempt, self.max_reconnect, self.reconnect_delay, self.rtsp_url,
            )
            self._stop_event.wait(self.reconnect_delay)

        # 重连次数耗尽 -> 报警（错误状态 + 日志）
        with self._status_lock:
            self.state = "error"
            self.last_error = f"视频流连接失败超过 {self.max_reconnect} 次"
        logger.error(
            "摄像头 %s 重连 %d 次失败，进入 error 状态（将持续轮询恢复）: %s",
            self.camera_id, self.max_reconnect, self.rtsp_url,
        )

    def _handle_stream_loss(self) -> None:
        """断流处理：释放旧连接，立即触发重连。"""
        logger.warning("摄像头 %s 视频流断开，准备重连", self.camera_id)
        self._release_capture()
        self._capture = None
        self._connect()

    def _release_capture(self) -> None:
        """安全释放 VideoCapture（另一线程 stop 时也调用，需容错）。"""
        cap, self._capture = self._capture, None
        if cap is not None:
            try:
                cap.release()
            except Exception:
                pass

    # ------------------------------------------------------------------ #
    # 检测与违规处理
    # ------------------------------------------------------------------ #
    def _process_frame(self, frame) -> None:
        """对一帧执行检测并处理违规。推理异常被兜底，不影响主循环。"""
        try:
            detections = self._detector.predict(
                frame,
                conf=self.conf_threshold,
                iou=self.iou_threshold,
                enabled_classes=self.enabled_classes or None,
            )
        except Exception as exc:
            logger.error("摄像头 %s 推理异常: %s", self.camera_id, exc, exc_info=True)
            return

        # 同一帧内同类型违规只处理一次（置信度最高者）：
        # 多人同类违规时一张整帧截图已能覆盖全部违规者，避免一帧内重复截图/告警。
        # 按置信度降序处理，先通过防抖的即保留，其余同类型跳过。
        seen_types: set[str] = set()
        for det in sorted(detections, key=lambda d: d.get("confidence", 0.0), reverse=True):
            bbox = det["bbox"]
            cx = (bbox["x1"] + bbox["x2"]) / 2.0
            cy = (bbox["y1"] + bbox["y2"]) / 2.0

            # ROI 过滤：目标中心不在 ROI 内则忽略
            if not self._roi.contains(cx, cy):
                continue

            violation_type = det["class_name"]
            if violation_type in seen_types:
                continue

            # 防抖：同类型 + 位置粗粒度 (x,y) 在冷却期内不重复触发
            if not self._debouncer.should_trigger(violation_type, cx, cy):
                continue

            seen_types.add(violation_type)
            self._handle_violation(frame, det)

    def _handle_violation(self, frame, det: dict) -> None:
        """处理一次违规：截图 + 裁剪 + 推送 + 记录。"""
        violation_type = det["class_name"]
        confidence = det["confidence"]
        bbox = det["bbox"]

        # 1) 保存整帧截图（失败不影响推送）
        screenshot_path, screenshot_filename = None, ""
        try:
            screenshot_path, screenshot_filename = shot.save_screenshot(
                frame, self.camera_id, violation_type,
                screenshot_dir=self.screenshot_dir, root=settings.screenshot_root,
            )
            # 2) 保存 bbox 裁剪图
            shot.save_crop(
                frame, bbox, self.camera_id, violation_type,
                screenshot_dir=self.screenshot_dir, root=settings.screenshot_root,
            )
        except Exception as exc:
            logger.error("摄像头 %s 截图失败: %s", self.camera_id, exc)

        # 3) 构建告警并推送
        ts = datetime.now().isoformat()
        event_id = f"{self.camera_id}_{datetime.now().strftime('%Y%m%d_%H%M%S')}_{violation_type}"
        alert = self._publisher.build_alert(
            event_id=event_id,
            violation_type=violation_type,
            confidence=confidence,
            screenshot_filename=screenshot_filename or violation_type,
            bbox=bbox,
            timestamp=ts,
        )
        pushed = self._publisher.publish_alert(alert)

        # 4) 更新状态与回放
        with self._status_lock:
            self.alert_count += 1
            self.last_alert_time = ts
        self._replay.append(
            {
                "event_id": event_id,
                "violation_type": violation_type,
                "confidence": round(confidence, 4),
                "severity": alert["_severity"],
                "timestamp": ts,
                "screenshot_url": alert["_screenshot_url"],
            }
        )
        logger.info(
            "摄像头 %s 违规: type=%s conf=%.3f push=%s shot=%s",
            self.camera_id, violation_type, confidence, pushed, screenshot_path,
        )

    # ------------------------------------------------------------------ #
    # 状态查询 / 停止
    # ------------------------------------------------------------------ #
    def _update_fps(self) -> None:
        """EMA 平滑统计读取帧率（检测线程内调用）。"""
        now = time.monotonic()
        dt = now - self._last_frame_time
        self._last_frame_time = now
        if dt <= 0:
            return
        inst = 1.0 / dt
        self._fps_ema = self._fps_ema * 0.9 + inst * 0.1 if self._fps_ema else inst
        with self._status_lock:
            self.fps = round(self._fps_ema, 1)

    def get_status(self) -> dict:
        """返回运行状态快照（供 /api/cameras/status 与 /api/cameras/start 使用）。"""
        with self._status_lock:
            return {
                "camera_id": self.camera_id,
                "camera_name": self.camera_name,
                "location_name": self.location_name,
                "state": self.state,
                "fps": round(self.fps, 1),
                "alert_count": self.alert_count,
                "last_alert_time": self.last_alert_time,
                "last_error": self.last_error,
            }

    def get_replay(self) -> list[dict]:
        """返回最近告警回放列表（从旧到新）。"""
        return list(self._replay)

    def stop(self) -> None:
        """停止线程：置停止标记、释放连接、停止推送器。"""
        logger.info("停止摄像头: %s", self.camera_id)
        self._stop_event.set()
        self._release_capture()
        if getattr(self, "_publisher", None) is not None:
            try:
                self._publisher.stop()
            except Exception as exc:
                logger.warning("摄像头 %s 推送器停止异常: %s", self.camera_id, exc)

    def _cleanup(self) -> None:
        """线程退出前的资源清理。"""
        self._release_capture()
        with self._status_lock:
            self.state = "stopped"
        logger.info("摄像头 %s 资源已清理", self.camera_id)
