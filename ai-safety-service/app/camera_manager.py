"""
多路摄像头统一管理（全局单例）。

职责：
    - 维护 {camera_id: CameraMonitor} 字典；
    - 启动 / 停止单路摄像头；
    - 服务启动时自动读取 configs/*.yaml 并启动全部摄像头；
    - 提供状态汇总、告警回放查询。
"""

from __future__ import annotations

import logging
import threading
from pathlib import Path

import yaml

from app.monitor import CameraMonitor

logger = logging.getLogger("ai_monitor.manager")


class CameraManager:
    """摄像头管理器单例。"""

    _instance: "CameraManager | None" = None
    _instance_lock = threading.Lock()

    def __init__(self) -> None:
        self._monitors: dict[str, CameraMonitor] = {}
        self._lock = threading.Lock()  # 保护 _monitors

    @classmethod
    def get_instance(cls) -> "CameraManager":
        """获取全局单例。"""
        with cls._instance_lock:
            if cls._instance is None:
                cls._instance = cls()
            return cls._instance

    # ------------------------------------------------------------------ #
    # 启动 / 停止
    # ------------------------------------------------------------------ #
    def start_camera(self, config: dict) -> dict:
        """
        启动一路摄像头监测（同 ID 已存在时先停止旧的）。

        Args:
            config: 摄像头配置字典，必须包含 camera_id 与 rtsp_url。

        Returns:
            该摄像头的最新状态快照。
        """
        camera_id = str(config.get("camera_id", "")).strip()
        if not camera_id:
            raise ValueError("配置缺少 camera_id")
        if not config.get("rtsp_url"):
            raise ValueError(f"摄像头 {camera_id} 配置缺少 rtsp_url")

        with self._lock:
            old = self._monitors.get(camera_id)
            if old is not None:
                logger.info("摄像头 %s 已存在，先停止旧实例", camera_id)
                old.stop()
                old.join(timeout=5)

            monitor = CameraMonitor(config)
            self._monitors[camera_id] = monitor
            monitor.start()

        logger.info("摄像头已启动: %s", camera_id)
        return monitor.get_status()

    def stop_camera(self, camera_id: str) -> bool:
        """
        停止并移除指定摄像头。

        Returns:
            是否成功停止（摄像头不存在时返回 False）。
        """
        with self._lock:
            monitor = self._monitors.pop(camera_id, None)
        if monitor is None:
            logger.warning("停止失败，摄像头不存在: %s", camera_id)
            return False
        monitor.stop()
        monitor.join(timeout=10)
        logger.info("摄像头已停止: %s", camera_id)
        return True

    def stop_all(self) -> None:
        """停止全部摄像头（服务关闭时调用）。"""
        with self._lock:
            monitors = list(self._monitors.values())
            self._monitors.clear()
        for m in monitors:
            try:
                m.stop()
            except Exception as exc:
                logger.warning("停止摄像头 %s 异常: %s", m.camera_id, exc)
        for m in monitors:
            m.join(timeout=10)
        logger.info("全部摄像头已停止（共 %d 路）", len(monitors))

    # ------------------------------------------------------------------ #
    # 自动加载配置
    # ------------------------------------------------------------------ #
    def start_from_configs(self, configs_dir: str | Path) -> int:
        """
        读取 configs 目录下所有 *.yaml，逐个启动摄像头。

        Args:
            configs_dir: 配置目录路径。

        Returns:
            成功启动的摄像头数量。
        """
        configs_dir = Path(configs_dir)
        if not configs_dir.is_dir():
            logger.warning("摄像头配置目录不存在: %s", configs_dir)
            return 0

        started = 0
        for yaml_file in sorted(configs_dir.glob("*.yaml")):
            try:
                config = yaml.safe_load(yaml_file.read_text(encoding="utf-8")) or {}
                if not isinstance(config, dict):
                    logger.warning("跳过无效配置（非字典）: %s", yaml_file.name)
                    continue
                if not config.get("camera_id"):
                    logger.warning("跳过缺少 camera_id 的配置: %s", yaml_file.name)
                    continue
                self.start_camera(config)
                started += 1
            except Exception as exc:
                logger.error("加载摄像头配置失败 %s: %s", yaml_file.name, exc, exc_info=True)
        logger.info("自动加载摄像头配置完成: 共 %d 路", started)
        return started

    # ------------------------------------------------------------------ #
    # 查询
    # ------------------------------------------------------------------ #
    def get_monitor(self, camera_id: str) -> CameraMonitor | None:
        """按 ID 获取监测线程实例。"""
        with self._lock:
            return self._monitors.get(camera_id)

    def get_status(self) -> list[dict]:
        """返回所有摄像头运行状态列表。"""
        with self._lock:
            monitors = list(self._monitors.values())
        return [m.get_status() for m in monitors]

    def get_replay(self, camera_id: str) -> list[dict] | None:
        """
        返回指定摄像头最近告警回放。

        Returns:
            告警摘要列表；摄像头不存在时返回 None。
        """
        monitor = self.get_monitor(camera_id)
        if monitor is None:
            return None
        return monitor.get_replay()
