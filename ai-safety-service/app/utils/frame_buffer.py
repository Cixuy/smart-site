"""
帧环形缓冲。

用途：摄像头线程持续写入最新帧，其他逻辑（截图/回放）可以随时取到
最新一帧的快照，同时避免持有过多内存。线程安全。
"""

from __future__ import annotations

import threading
from collections import deque

import numpy as np


class FrameBuffer:
    """线程安全的环形帧缓冲，只保留最近 N 帧。"""

    def __init__(self, maxlen: int = 32) -> None:
        """
        初始化缓冲。

        Args:
            maxlen: 最多保留的帧数，超过后最旧的帧被丢弃。
        """
        self._maxlen = max(1, int(maxlen))
        self._frames: deque = deque(maxlen=self._maxlen)
        self._lock = threading.RLock()

    def put(self, frame: np.ndarray) -> int:
        """
        写入一帧（内部拷贝，避免调用方后续修改影响缓冲内容）。

        Args:
            frame: BGR 图像帧 (H, W, 3)。

        Returns:
            当前缓冲中的帧数。
        """
        with self._lock:
            self._frames.append(frame.copy())
            return len(self._frames)

    def get_latest(self) -> np.ndarray | None:
        """获取最新一帧的拷贝；缓冲为空时返回 None。"""
        with self._lock:
            if not self._frames:
                return None
            return self._frames[-1].copy()

    def get_all(self) -> list[np.ndarray]:
        """获取全部缓冲帧的拷贝列表（从旧到新）。"""
        with self._lock:
            return [f.copy() for f in self._frames]

    def size(self) -> int:
        """当前缓冲帧数。"""
        with self._lock:
            return len(self._frames)

    def clear(self) -> None:
        """清空缓冲。"""
        with self._lock:
            self._frames.clear()
