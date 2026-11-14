#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
train_stage2.py — 第二阶段：960 分辨率小目标微调

背景:
    工地监控中远处人头只有 20~40px，640 分辨率下小目标特征过少。
    第二阶段用 960 分辨率继续训练，提升小目标召回。

策略要点:
    - 加载 Stage1 的 last.pt 继续训练（热启动，避免从头来）
    - imgsz=960（分辨率翻倍），batch=8（显存翻倍占用，batch 减半防 OOM）
    - lr0=2e-4（二阶段更小学习率，精细调优）
    - patience=15, cos_lr=True
    - close_mosaic=10：最后 10 轮关闭 mosaic，边框回归更稳定
    - copy_paste=0.3：粘贴增强，强化遮挡目标学习
    - amp=True

输出:
    runs/construction/yolo11s_stage2_960/weights/best.pt
    runs/best_stage2.txt
    runs/map_stage2.txt（与 stage1 对比输出）

用法:
    python train_stage2.py
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
STAGE1_LAST = ROOT / "runs" / "construction" / "yolo11s_stage1" / "weights" / "last.pt"
PROJECT = ROOT / "runs" / "construction"
NAME = "yolo11s_stage2_960"
RUNS_DIR = ROOT / "runs"


def main() -> int:
    if not DATA.exists():
        print(f"[ERROR] 数据集配置不存在: {DATA}")
        return 1
    if not STAGE1_LAST.exists():
        print(f"[ERROR] 未找到 Stage1 权重: {STAGE1_LAST}")
        print("        请先运行 python train_stage1.py")
        return 1

    device = 0 if torch.cuda.is_available() else "cpu"
    print("=" * 60)
    print(f"Stage 2 | imgsz=960 | epochs=60 | batch=8 | device={device}")
    print(f"从 Stage1 热启动: {STAGE1_LAST}")
    print("=" * 60)

    t0 = time.time()
    model = YOLO(str(STAGE1_LAST))
    model.train(
        data=str(DATA),
        epochs=60,
        imgsz=960,
        batch=8,              # 分辨率翻倍，batch 减半防 OOM
        lr0=2e-4,             # 二阶段更小学习率，精细调优
        cos_lr=True,
        patience=15,
        close_mosaic=10,      # 最后 10 轮关闭 mosaic，框更稳
        copy_paste=0.3,
        amp=True,
        project=str(PROJECT),
        name=NAME,
        workers=4,
        device=device,
    )
    elapsed = time.time() - t0
    print(f"\n[Stage2] 训练完成，耗时 {elapsed / 60:.1f} 分钟")

    # ---- 自动验证 ----
    best_pt = PROJECT / NAME / "weights" / "best.pt"
    if not best_pt.exists():
        best_pt = PROJECT / NAME / "weights" / "last.pt"
        print(f"[Stage2][WARN] 未找到 best.pt，回退使用 last.pt")

    finetuned = YOLO(str(best_pt))
    metrics = finetuned.val(data=str(DATA), imgsz=960, verbose=False)
    map50 = float(metrics.box.map50)
    map5095 = float(metrics.box.map)
    print("-" * 60)
    print(f"[Stage2] mAP@0.5      = {map50:.4f}")
    print(f"[Stage2] mAP@0.5:0.95 = {map5095:.4f}")
    print("-" * 60)

    # ---- 两阶段 mAP 对比 ----
    map_stage1 = RUNS_DIR / "map_stage1.txt"
    if map_stage1.exists():
        lines = map_stage1.read_text(encoding="utf-8").split()
        if len(lines) >= 2:
            s1_50, s1_95 = float(lines[0]), float(lines[1])
            print("两阶段 mAP 对比:")
            print(f"  {'阶段':<16}{'imgsz':<8}{'mAP@0.5':<12}{'mAP@0.5:0.95':<12}")
            print(f"  {'Stage1(640)':<16}{640:<8}{s1_50:<12.4f}{s1_95:<12.4f}")
            print(f"  {'Stage2(960)':<16}{960:<8}{map50:<12.4f}{map5095:<12.4f}")
            delta = map50 - s1_50
            print(f"  mAP@0.5 提升: {delta:+.4f}")
    else:
        print("[Stage2][WARN] 未找到 map_stage1.txt，跳过对比")

    RUNS_DIR.mkdir(exist_ok=True)
    (RUNS_DIR / "best_stage2.txt").write_text(str(best_pt), encoding="utf-8")
    (RUNS_DIR / "map_stage2.txt").write_text(
        f"{map50:.4f}\n{map5095:.4f}\n", encoding="utf-8"
    )
    print(f"[Stage2] best.pt 路径已保存 -> {RUNS_DIR / 'best_stage2.txt'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
