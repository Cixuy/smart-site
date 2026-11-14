"""
YOLO11 检测器单例封装。

设计要点：
    - 模型只加载一次到 GPU，多路摄像头线程共享同一个实例（单例模式）；
    - 支持半精度推理（model.half()）节省显存；
    - 支持按类别名称过滤（enabled_classes）；
    - predict() 返回统一 dict 结构：{class_name, confidence, bbox}。

线程安全说明：Ultralytics YOLO 的 predict() 在多线程下共享同一模型实例
是官方支持的（内部按线程上下文管理）。若并发量极大导致显存/推理竞争，
可在 CameraMonitor 内对同摄像头串行调用即可满足当前需求。
"""

from __future__ import annotations

import logging
import threading

import torch
from ultralytics import YOLO

from app.config import settings

logger = logging.getLogger("ai_monitor.detector")


class YOLODetector:
    """YOLO 检测器单例，全进程共享一个模型实例。"""

    _instance: "YOLODetector | None" = None
    _instance_lock = threading.Lock()

    def __init__(self, model_path: str, device: str = "auto", half: bool = True) -> None:
        """
        Args:
            model_path: .pt 模型文件路径（相对/绝对均可）。
            device:     auto / cuda / cpu；auto 时优先 GPU。
            half:       是否启用 FP16 半精度推理（仅 GPU 有效）。
        """
        self.model_path = str(model_path)
        self.device = self._resolve_device(device)
        self.half = half and self.device.startswith("cuda")

        logger.info("加载 YOLO 模型: %s (device=%s, half=%s)", self.model_path, self.device, self.half)
        self.model = YOLO(self.model_path)
        if self.half:
            self.model = self.model.half()  # FP16 权重，减少显存与提升吞吐
        self.names: dict[int, str] = self.model.names  # {id: 类别名}
        logger.info(
            "模型加载完成: 类别数=%d, 类别=%s", len(self.names), list(self.names.values())
        )

        # 预热：跑一次空推理，确保 CUDA 上下文 / TensorRT 等就绪
        # 注意：新版 ultralytics 已弃用 predict(half=...)，半精度通过 model.half()
        # 在初始化时完成，predict 会自动继承模型自身精度，因此这里不传 half 参数。
        try:
            import numpy as np

            dummy = np.zeros((64, 64, 3), dtype=np.uint8)
            self.model.predict(dummy, conf=0.5, device=self.device, verbose=False)
            logger.info("模型预热完成")
        except Exception as exc:  # 预热失败不致命，仅记录
            logger.warning("模型预热失败（可忽略）: %s", exc)

    @classmethod
    def get_instance(
        cls,
        model_path: str | None = None,
        device: str | None = None,
        half: bool | None = None,
    ) -> "YOLODetector":
        """
        获取全局单例。首次调用时加载模型，后续直接复用。

        Args:
            model_path: 缺省使用全局配置 settings.model_path。
            device:     缺省使用全局配置 settings.device。
            half:       缺省使用全局配置 settings.half_precision。
        """
        with cls._instance_lock:
            if cls._instance is None:
                cls._instance = cls(
                    model_path or str(settings.model_path),
                    device or settings.device,
                    settings.half_precision if half is None else half,
                )
            return cls._instance

    @classmethod
    def is_loaded(cls) -> bool:
        """模型是否已加载（供 /api/health 探测，避免触发加载）。"""
        return cls._instance is not None

    @staticmethod
    def _resolve_device(device: str) -> str:
        """解析设备：auto 时按 CUDA 可用性决定。"""
        device = (device or "auto").strip().lower()
        if device == "auto":
            return "cuda" if torch.cuda.is_available() else "cpu"
        return device

    def predict(
        self,
        frame,
        conf: float = 0.25,
        iou: float = 0.45,
        enabled_classes: list[str] | None = None,
    ) -> list[dict]:
        """
        对单帧执行检测。

        Args:
            frame:           BGR 图像 (H, W, 3)。
            conf:            置信度阈值。
            iou:             NMS IoU 阈值。
            enabled_classes: 需要保留的类别名列表；None/空 表示全部类别。

        Returns:
            [{"class_name": str, "confidence": float,
              "bbox": {"x1":..,"y1":..,"x2":..,"y2":..}}, ...]
        """
        # 将类别名转成索引，传给 Ultralytics 可提前过滤、提升推理速度
        class_ids = None
        if enabled_classes:
            wanted = set(enabled_classes)
            class_ids = [idx for idx, name in self.names.items() if name in wanted]
            if not class_ids:
                return []

        # 半精度已在初始化时通过 model.half() 完成（见 __init__），
        # predict 不再传 half=，避免新版 ultralytics 的弃用警告。
        results = self.model.predict(
            frame,
            conf=conf,
            iou=iou,
            classes=class_ids,
            device=self.device,
            verbose=False,
        )

        detections: list[dict] = []
        for result in results:
            boxes = result.boxes
            if boxes is None or len(boxes) == 0:
                continue
            xyxy = boxes.xyxy.cpu().numpy()
            confs = boxes.conf.cpu().numpy()
            cls_ids = boxes.cls.cpu().numpy().astype(int)
            for x1, y1, x2, y2, c, cid in zip(
                xyxy[:, 0], xyxy[:, 1], xyxy[:, 2], xyxy[:, 3], confs, cls_ids
            ):
                detections.append(
                    {
                        "class_name": self.names.get(int(cid), str(cid)),
                        "confidence": float(c),
                        "bbox": {
                            "x1": float(x1),
                            "y1": float(y1),
                            "x2": float(x2),
                            "y2": float(y2),
                        },
                    }
                )
        return detections

    def gpu_info(self) -> dict:
        """返回 GPU 状态摘要（供 /api/health 使用）。"""
        if not torch.cuda.is_available():
            return {"gpu_available": False, "gpu_name": None, "device": self.device}
        return {
            "gpu_available": True,
            "gpu_name": torch.cuda.get_device_name(0),
            "device": self.device,
        }
