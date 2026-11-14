"""
违规防抖器。

需求：同一违规类型 + 位置粗粒度（bbox 中心 (x, y) 分格）在冷却时间内
不重复触发告警，避免同一个人长时间违规导致刷屏推送。

x/y 均按网格划分（默认 50px 一格），同一目标轻微移动不会导致键变化；
加入 y 维度后，画面中不同位置（不同 y）的同类型目标不会被误判为同一个，
避免多人场景下防抖被互相绕过。

线程安全，可被多线程共享（当前每路摄像头一个实例，单线程访问）。
"""

from __future__ import annotations

import threading
import time


class Debouncer:
    """
    冷却防抖器，支持两种抑制模式：

    - grid（默认）：键 = f"{类型}:{x格}:{y格}"，同类型不同位置可独立触发，
      适合多人违规需分别上报的场景；但对移动目标（跨格即换键）抑制较弱。
    - global：键 = f"{类型}"，冷却期内同类型无论位置只触发一次，
      抑制最强，适合测试/防刷屏，避免大量重复截图与告警。
    """

    def __init__(
        self,
        cooldown_seconds: float = 15.0,
        mode: str = "grid",
        grid_x: int = 50,
        grid_y: int = 50,
    ) -> None:
        """
        Args:
            cooldown_seconds: 冷却时间（秒）。同一键在此时间内只允许触发一次。
            mode:             "grid"（位置网格）或 "global"（同类型全局）。
            grid_x:           x 方向网格像素大小（mode="grid" 时生效）。
            grid_y:           y 方向网格像素大小（mode="grid" 时生效）。
        """
        self._cooldown = max(0.0, float(cooldown_seconds))
        self._mode = mode if mode in ("grid", "global") else "grid"
        self._grid_x = max(1, int(grid_x))
        self._grid_y = max(1, int(grid_y))
        self._records: dict[str, float] = {}  # key -> 上次触发时间戳
        self._lock = threading.Lock()

    def _make_key(self, violation_type: str, bbox_center_x: float, bbox_center_y: float) -> str:
        """
        生成防抖键：
        - global 模式：仅类型（同类型全局冷却）；
        - grid 模式：类型 + 中心点 (x, y) 所在网格（网格越大对移动越不敏感）。
        """
        if self._mode == "global":
            return f"{violation_type}:*"
        gx = int(float(bbox_center_x) // self._grid_x)
        gy = int(float(bbox_center_y) // self._grid_y)
        return f"{violation_type}:{gx}:{gy}"

    def should_trigger(
        self,
        violation_type: str,
        bbox_center_x: float,
        bbox_center_y: float,
        now: float | None = None,
    ) -> bool:
        """
        判断是否允许触发一次告警，并顺带清理过期记录。

        Args:
            violation_type: 违规类别名（如 NO-Hardhat）。
            bbox_center_x:  目标 bbox 中心点 x 坐标。
            bbox_center_y:  目标 bbox 中心点 y 坐标。
            now:            当前时间戳（测试可注入）。

        Returns:
            True 表示冷却已过、允许触发；False 表示处于冷却期。
        """
        now = time.time() if now is None else float(now)
        key = self._make_key(violation_type, bbox_center_x, bbox_center_y)

        with self._lock:
            last = self._records.get(key)
            if last is not None and (now - last) < self._cooldown:
                return False
            self._records[key] = now
            self._prune_locked(now)
            return True

    def _prune_locked(self, now: float) -> None:
        """清理超过冷却期两倍的记录，防止字典无限增长（须持锁调用）。"""
        expired_keys = [
            k for k, t in self._records.items() if (now - t) > self._cooldown * 2
        ]
        for k in expired_keys:
            del self._records[k]

    def clear(self) -> None:
        """清空所有防抖记录（例如摄像头重连后重置状态）。"""
        with self._lock:
            self._records.clear()

    def size(self) -> int:
        """当前活跃的防抖键数量（调试用）。"""
        with self._lock:
            return len(self._records)
