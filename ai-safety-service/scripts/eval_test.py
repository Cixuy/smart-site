#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
eval_test.py — 测试集完整评估 + 预测可视化

功能:
    1. 用 Stage2 best.pt 在测试集 (split=test, 82 张) 上完整评估
       输出全类别与分类别 P/R/mAP@0.5/mAP@0.5:0.95
    2. 随机抽 8 张测试图做预测可视化（带框标注），保存到 runs/construction/pred_test/
    3. 汇总写入 runs/test_eval.txt

用法:
    python eval_test.py
"""

import random
import sys
from pathlib import Path

from ultralytics import YOLO

for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

ROOT = Path(__file__).resolve().parent.parent  # 项目根目录（本脚本位于 scripts/ 子目录）
DATA = ROOT / "datasets" / "css-data" / "data_final.yaml"
BEST = ROOT / "runs" / "construction" / "yolo11s_stage2_960" / "weights" / "best.pt"
TEST_IMAGES = ROOT / "datasets" / "css-data" / "test" / "images"
OUT = ROOT / "runs" / "construction"


def main() -> int:
    if not BEST.exists() or not DATA.exists():
        print(f"[ERROR] 权重或数据缺失: {BEST} / {DATA}")
        return 1

    model = YOLO(str(BEST))
    names = model.names

    # ---- 1. 测试集完整评估 ----
    print("=" * 60)
    print("测试集评估 (split=test, imgsz=960)")
    print("=" * 60)
    metrics = model.val(
        data=str(DATA),
        split="test",
        imgsz=960,
        project=str(OUT),
        name="test_eval",
        plots=True,
        verbose=False,
    )

    map50 = float(metrics.box.map50)
    map5095 = float(metrics.box.map)
    print(f"全部: mAP@0.5 = {map50:.4f} | mAP@0.5:0.95 = {map5095:.4f}")

    # 分类别指标（按 ap_class_index 对齐）
    print("-" * 60)
    print(f"{'类别':<16}{'P':<8}{'R':<8}{'mAP@0.5':<10}{'mAP@0.5:0.95':<12}")
    rows = []
    for i, c in enumerate(metrics.box.ap_class_index):
        nm = names[int(c)]
        p, r = float(metrics.box.p[i]), float(metrics.box.r[i])
        a50, a5095 = float(metrics.box.ap50[i]), float(metrics.box.ap[i])
        rows.append((nm, p, r, a50, a5095))
        print(f"{nm:<16}{p:<8.3f}{r:<8.3f}{a50:<10.3f}{a5095:<12.3f}")
    print("-" * 60)

    # ---- 2. 预测可视化（随机抽 8 张）----
    print("\n生成预测可视化 ...")
    imgs = sorted(TEST_IMAGES.glob("*.jpg"))
    if not imgs:
        imgs = sorted(TEST_IMAGES.glob("*.png"))
    random.seed(0)
    sample = random.sample(imgs, min(8, len(imgs)))
    print(f"抽取 {len(sample)} 张: {[p.name for p in sample]}")

    _ = model.predict(
        [str(p) for p in sample],
        imgsz=960,
        conf=0.25,
        save=True,
        project=str(OUT),
        name="pred_test",
        verbose=False,
    )
    pred_dir = OUT / "pred_test"
    print(f"可视化已保存 -> {pred_dir}")

    # ---- 3. 汇总写入文件 ----
    lines = [
        f"mAP@0.5 = {map50:.4f}",
        f"mAP@0.5:0.95 = {map5095:.4f}",
        "class,precision,recall,mAP50,mAP50-95",
    ]
    lines += [f"{nm},{p:.4f},{r:.4f},{a50:.4f},{a5095:.4f}" for nm, p, r, a50, a5095 in rows]
    (ROOT / "runs" / "test_eval.txt").write_text("\n".join(lines), encoding="utf-8")
    print(f"\n汇总已保存 -> runs/test_eval.txt")
    return 0


if __name__ == "__main__":
    sys.exit(main())
