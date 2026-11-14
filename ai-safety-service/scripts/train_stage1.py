#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
train_stage1.py — 第一阶段：640 分辨率快速收敛基线

目标:
    用较低分辨率(640)快速跑通全流程，确认数据/代码无误，得到可用基线。

策略要点:
    - 加载 yolo11s.pt 预训练权重（本地无则自动从 GitHub 下载）
    - imgsz=640, epochs=100
    - AdamW + lr0=0.001 + cosine 学习率
    - patience=20 早停；amp=True 半精度省显存
    - mosaic=0.8 + copy_paste=0.2（工地遮挡多，粘贴增强帮助被遮挡目标学习）
    - hsv_v=0.4 / hsv_s=0.3 模拟粉尘、逆光、阴晴变化
    - batch 按显存自动调整: 默认 16，<=8G 用 8，<=4G 用 4

输出:
    runs/construction/yolo11s_stage1/weights/best.pt   # 最优权重
    runs/best_stage1.txt                               # best.pt 绝对路径
    runs/map_stage1.txt                                # 两行: mAP@0.5 / mAP@0.5:0.95（供 stage2 对比）

用法:
    python train_stage1.py
"""

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
DATA = ROOT / "datasets" / "css-data" / "data_final.yaml"
if not DATA.exists():
    DATA = ROOT / "data_final.yaml"  # 兼容 prepare_data.py 输出在根目录的情况
WEIGHTS = ROOT / "weights" / "yolo11s.pt"
PROJECT = ROOT / "runs" / "construction"
NAME = "yolo11s_stage1"
RUNS_DIR = ROOT / "runs"


def auto_batch(default: int = 16, mem_8g: int = 8, mem_4g: int = 4) -> int:
    """按显存自动调整 batch：<=4G 用 mem_4g，<=8G 用 mem_8g，否则用 default。"""
    if torch.cuda.is_available():
        gb = torch.cuda.get_device_properties(0).total_memory / 1024 ** 3
        print(f"[Stage1] 检测到显存 {gb:.1f} GB")
        if gb <= 4.5:
            return mem_4g
        if gb <= 8.5:
            return mem_8g
    return default


def main() -> int:
    if not DATA.exists():
        print(f"[ERROR] 数据集配置不存在: {DATA}")
        print("        请先确认 datasets/css-data/data_final.yaml 已生成")
        return 1

    batch = auto_batch()
    device = 0 if torch.cuda.is_available() else "cpu"
    print("=" * 60)
    print(f"Stage 1 | imgsz=640 | epochs=100 | batch={batch} | device={device}")
    print("=" * 60)

    t0 = time.time()
    model = YOLO(str(WEIGHTS))  # 本地没有 yolo11s.pt 会自动下载
    model.train(
        data=str(DATA),
        epochs=100,
        imgsz=640,
        batch=batch,
        optimizer="AdamW",
        lr0=0.001,
        cos_lr=True,
        patience=20,          # 20 轮验证指标不涨则早停
        amp=True,             # 半精度，省显存加速
        mosaic=0.8,
        copy_paste=0.2,
        hsv_v=0.4,            # 明暗变化（逆光/阴天）
        hsv_s=0.3,            # 饱和度变化（粉尘/雾霾）
        project=str(PROJECT),
        name=NAME,
        workers=4,
        device=device,
    )
    elapsed = time.time() - t0
    print(f"\n[Stage1] 训练完成，耗时 {elapsed / 60:.1f} 分钟")

    # ---- 自动验证: 加载 best.pt（早停极早或失败时回退 last.pt）----
    best_pt = PROJECT / NAME / "weights" / "best.pt"
    if not best_pt.exists():
        best_pt = PROJECT / NAME / "weights" / "last.pt"
        print(f"[Stage1][WARN] 未找到 best.pt，回退使用 last.pt")

    finetuned = YOLO(str(best_pt))
    metrics = finetuned.val(data=str(DATA), imgsz=640, verbose=False)
    map50 = float(metrics.box.map50)          # mAP@0.5
    map5095 = float(metrics.box.map)          # mAP@0.5:0.95
    print("-" * 60)
    print(f"[Stage1] mAP@0.5      = {map50:.4f}")
    print(f"[Stage1] mAP@0.5:0.95 = {map5095:.4f}")
    print("-" * 60)

    RUNS_DIR.mkdir(exist_ok=True)
    (RUNS_DIR / "best_stage1.txt").write_text(str(best_pt), encoding="utf-8")
    (RUNS_DIR / "map_stage1.txt").write_text(
        f"{map50:.4f}\n{map5095:.4f}\n", encoding="utf-8"
    )
    print(f"[Stage1] best.pt 路径已保存 -> {RUNS_DIR / 'best_stage1.txt'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
