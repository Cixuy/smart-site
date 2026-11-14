"""
ROI 多边形区域过滤。

需求：只检测/告警落在指定多边形区域内的目标（例如只关注施工区域，
忽略镜头边缘的通道）。未配置 ROI 时默认放行全部目标。
"""

from __future__ import annotations

import cv2
import numpy as np


class ROIFilter:
    """基于多边形包含关系判断目标是否在感兴趣区域内。"""

    def __init__(self, polygon: list[list[float]] | None = None) -> None:
        """
        Args:
            polygon: 多边形顶点列表，如 [[x1,y1],[x2,y2],...]；
                     None / 空 / 顶点不足 3 个视为未启用。
        """
        self._polygon: np.ndarray | None = None
        self.set_polygon(polygon)

    def set_polygon(self, polygon: list[list[float]] | None) -> None:
        """重置 ROI 多边形。"""
        if polygon and len(polygon) >= 3:
            arr = np.asarray(polygon, dtype=np.float32).reshape(-1, 2)
            if len(arr) >= 3:
                self._polygon = arr
            else:
                self._polygon = None
        else:
            self._polygon = None

    @property
    def enabled(self) -> bool:
        """ROI 是否启用。"""
        return self._polygon is not None

    def contains(self, x: float, y: float) -> bool:
        """
        判断点 (x, y) 是否落在 ROI 内（含边界）。

        Args:
            x: 目标点 x 坐标（通常为 bbox 中心）。
            y: 目标点 y 坐标。

        Returns:
            ROI 未启用时恒为 True；启用时返回点在多边形内/边上。
        """
        if self._polygon is None:
            return True
        # pointPolygonTest: 返回值 >= 0 表示点在多边形内部或边上
        result = cv2.pointPolygonTest(self._polygon, (float(x), float(y)), False)
        return result >= 0

    def contains_bbox(self, x1: float, y1: float, x2: float, y2: float) -> bool:
        """判断 bbox 中心点是否在 ROI 内（快捷方法）。"""
        cx, cy = (x1 + x2) / 2.0, (y1 + y2) / 2.0
        return self.contains(cx, cy)

    def __repr__(self) -> str:  # pragma: no cover - 调试用
        return f"ROIFilter(enabled={self.enabled}, polygon={self._polygon.tolist() if self._polygon else None})"
