#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
export_model.py — 导出 Stage2 最佳模型 + 推理速度 benchmark

功能:
    1. 加载 runs/construction/yolo11s_stage2_960/weights/best.pt
    2. 导出 ONNX（方便后续 TensorRT 加速）
    3. 导出 TorchScript（PyTorch 部署备选）
    4. 复制 pt 到 models/construction_safety_v1.pt（目录存在时）
    5. 打印模型文件大小 + 100 次前向平均耗时/FPS

用法:
    python export_model.py

说明:
    - ONNX 导出默认 imgsz=960（与训练一致），opset=12
    - 如需 TensorRT 半精度，可把 onnx 交给 trtexec --fp16 转换
    - 若安装 onnxslim 可把 simplify 改为 True 以获得精简图
"""

import shutil
import sys
import time
from pathlib import Path

import torch
from ultralytics import YOLO

# Windows 控制台 UTF-8 兼容
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

ROOT = Path(__file__).resolve().parent.parent  # 项目根目录（本脚本位于 scripts/ 子目录）
BEST_PT = ROOT / "runs" / "construction" / "yolo11s_stage2_960" / "weights" / "best.pt"
MODELS_DIR = ROOT / "models"          # 即 ../ai-safety-service/models/
DEPLOY_NAME = "construction_safety_v1.pt"
IMGSZ = 960


def fmt_size(path: Path) -> str:
    mb = path.stat().st_size / 1024 / 1024
    return f"{mb:.1f} MB"


def main() -> int:
    if not BEST_PT.exists():
        print(f"[ERROR] 未找到 Stage2 权重: {BEST_PT}")
        print("        请先运行 train_all.sh 或 train_stage2.py")
        return 1

    device = 0 if torch.cuda.is_available() else "cpu"
    print("=" * 60)
    print(f"加载权重: {BEST_PT} ({fmt_size(BEST_PT)})")
    model = YOLO(str(BEST_PT))

    # ---- 1. 导出 ONNX ----
    print("\n[1/4] 导出 ONNX (imgsz=960, opset=12) ...")
    onnx_path = model.export(
        format="onnx", imgsz=IMGSZ, opset=12,
        simplify=False,   # 需 onnxslim 依赖，可选开启
    )
    onnx_path = Path(onnx_path)
    print(f"  [OK] {onnx_path} ({fmt_size(onnx_path)})")

    # ---- 2. 导出 TorchScript ----
    print("\n[2/4] 导出 TorchScript ...")
    ts_path = model.export(format="torchscript", imgsz=IMGSZ)
    ts_path = Path(ts_path)
    print(f"  [OK] {ts_path} ({fmt_size(ts_path)})")

    # ---- 3. 复制 pt 到 models 目录 ----
    print("\n[3/4] 复制部署权重 ...")
    if MODELS_DIR.exists():
        dst = MODELS_DIR / DEPLOY_NAME
        shutil.copy2(BEST_PT, dst)
        print(f"  [OK] 已复制 -> {dst} ({fmt_size(dst)})")
    else:
        print(f"  [SKIP] 目录不存在: {MODELS_DIR}（跳过复制，如需请先 mkdir）")

    # ---- 4. 推理速度 benchmark（PyTorch FP32，GPU 或 CPU）----
    print(f"\n[4/4] 推理 benchmark: 随机图 1x3x{IMGSZ}x{IMGSZ}，100 次取平均")
    m = model.model
    m.to(device).eval()
    x = torch.randn(1, 3, IMGSZ, IMGSZ).to(device)

    with torch.no_grad():
        for _ in range(20):          # warmup
            m(x)
        if device == "cuda":
            torch.cuda.synchronize()
        t0 = time.perf_counter()
        for _ in range(100):
            m(x)
        if device == "cuda":
            torch.cuda.synchronize()
        dt_ms = (time.perf_counter() - t0) / 100 * 1000

    print(f"  平均耗时: {dt_ms:.1f} ms/张 | 约 {1000 / dt_ms:.1f} FPS (device={device})")
    print("=" * 60)
    print("导出完成。TensorRT 加速可执行: "
          f"trtexec --onnx={onnx_path} --fp16 --saveEngine={onnx_path.with_suffix('.engine')}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
