#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
inspect_dataset.py — 数据集检查与可视化

功能:
    1. 读取 datasets/css-data/data.yaml，打印基本信息（类别数、类别名、各集图片数）
    2. 随机抽取 5 张训练图，绘制 bounding box 标注 -> dataset_preview.png
    3. 统计各类别标注数量，绘制柱状图 -> class_distribution.png
    4. 数据质量检查（空 label / 图片损坏 / 类别 ID 越界 / 文件不匹配），输出报告

用法:
    python inspect_dataset.py [--data path/to/data.yaml] [--seed 42]

输出（默认保存到 assets/ 目录）:
    assets/dataset_preview.png     # 5 张标注可视化
    assets/class_distribution.png  # 类别分布柱状图
"""

import argparse
import random
import sys
from collections import Counter
from pathlib import Path

import cv2
import matplotlib
import numpy as np
import yaml

matplotlib.use("Agg")  # 无界面后端，避免在服务器/无显示器环境报错
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib import colormaps  # noqa: E402
from matplotlib.patches import Rectangle  # noqa: E402


def get_cmap(n: int):
    """返回 n 色的 colormap（替代已移除的 cm.get_cmap(name, N)）。"""
    return colormaps["tab20"].resampled(max(n, 20))

# Windows 控制台 UTF-8 兼容
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

ROOT = Path(__file__).resolve().parent.parent  # 项目根目录（本脚本位于 scripts/ 子目录）
DEFAULT_YAML = ROOT / "datasets" / "css-data" / "data.yaml"

# 检查项统计（累计所有 split）
PROBLEMS = {"empty_label": [], "corrupt_image": [], "id_overflow": [], "unmatched": []}


# ---------------------------------------------------------------------------
# 工具函数
# ---------------------------------------------------------------------------
def norm_bbox_to_xyxy(cx, cy, w, h, img_w, img_h):
    """YOLO 归一化格式 (cx, cy, w, h) -> 像素 (x1, y1, x2, y2)。"""
    x1 = (cx - w / 2) * img_w
    y1 = (cy - h / 2) * img_h
    x2 = (cx + w / 2) * img_w
    y2 = (cy + h / 2) * img_h
    return x1, y1, x2, y2


def read_yaml(path: Path):
    with open(path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def names_to_list(names) -> list:
    """data.yaml 的 names 可能是 list 或 dict，统一为按 id 顺序的 list。"""
    if isinstance(names, list):
        return list(names)
    if isinstance(names, dict):
        return [names[i] for i in range(len(names))]
    raise ValueError(f"无法解析 names: {type(names)}")


# ---------------------------------------------------------------------------
# 1. 基本信息
# ---------------------------------------------------------------------------
def report_basic(cfg: dict, data_root: Path):
    names = names_to_list(cfg.get("names", []))
    nc = int(cfg.get("nc", len(names)))
    print("=" * 60)
    print("1. 数据集基本信息")
    print("=" * 60)
    print(f"  Roboflow 项目: {cfg.get('roboflow', {}).get('project', 'N/A')}")
    print(f"  Roboflow 版本: v{cfg.get('roboflow', {}).get('version', 'N/A')}")
    print(f"  类别数 nc    : {nc}")
    for i, n in enumerate(names):
        print(f"    {i}: {n}")
    print()

    for split in ("train", "val", "test"):
        rel = cfg.get(split)
        if not rel:
            continue
        img_dir = data_root / rel / "images"
        n_img = len(list(img_dir.glob("*"))) if img_dir.exists() else 0
        n_lbl = len(list((data_root / rel / "labels").glob("*.txt"))) \
            if (data_root / rel / "labels").exists() else 0
        print(f"  [{split}] 图片 {n_img} 张, 标注文件 {n_lbl} 个  ({rel})")
    print()


# ---------------------------------------------------------------------------
# 2. 随机 5 张图可视化
# ---------------------------------------------------------------------------
def draw_preview(cfg: dict, data_root: Path, names: list, seed: int = 42):
    print("=" * 60)
    print("2. 标注可视化（随机 5 张训练图）-> dataset_preview.png")
    print("=" * 60)
    rel = cfg.get("train")
    if not rel:
        print("  [SKIP] data.yaml 中没有 train 字段")
        return
    img_dir = data_root / rel / "images"
    lbl_dir = data_root / rel / "labels"
    imgs = sorted(img_dir.glob("*")) if img_dir.exists() else []
    if not imgs:
        print("  [WARN] 训练集图片目录为空，跳过可视化")
        return

    random.seed(seed)
    sampled = random.sample(imgs, min(5, len(imgs)))
    colors = get_cmap(len(names))

    fig, axes = plt.subplots(1, len(sampled), figsize=(4 * len(sampled), 4))
    if len(sampled) == 1:
        axes = [axes]

    for ax, img_path in zip(axes, sampled):
        img = cv2.imread(str(img_path))
        if img is None:
            ax.set_title("corrupt")
            ax.axis("off")
            continue
        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        h, w = img.shape[:2]
        ax.imshow(img)

        lbl_file = lbl_dir / (img_path.stem + ".txt")
        if lbl_file.exists():
            with open(lbl_file, "r", encoding="utf-8") as f:
                for line in f:
                    parts = line.split()
                    if len(parts) < 5:
                        continue
                    cid = int(parts[0])
                    x1, y1, x2, y2 = norm_bbox_to_xyxy(
                        *[float(p) for p in parts[1:5]], w, h)
                    color = colors(cid if cid < len(names) else len(names) - 1)
                    ax.add_patch(Rectangle((x1, y1), x2 - x1, y2 - y1,
                                           fill=False, edgecolor=color, lw=1.5))
                    name = names[cid] if cid < len(names) else f"id{cid}"
                    ax.text(x1, max(y1 - 3, 0), name, fontsize=7, color=color,
                            bbox=dict(facecolor="white", alpha=0.7, pad=1))
        ax.set_title(img_path.name, fontsize=8)
        ax.axis("off")

    fig.suptitle("Dataset Preview (random 5)", fontsize=13)
    fig.tight_layout()
    out = ROOT / "assets" / "dataset_preview.png"
    fig.savefig(out, dpi=120, bbox_inches="tight")
    plt.close(fig)
    print(f"  [OK] 已保存: {out}")


# ---------------------------------------------------------------------------
# 3. 类别分布
# ---------------------------------------------------------------------------
def draw_distribution(cfg: dict, data_root: Path, names: list):
    print("=" * 60)
    print("3. 类别标注统计 -> class_distribution.png")
    print("=" * 60)
    counter = Counter()
    for split in ("train", "val", "test"):
        lbl_dir = data_root / cfg[split] / "labels" if cfg.get(split) else None
        if not lbl_dir or not lbl_dir.exists():
            continue
        for f in lbl_dir.glob("*.txt"):
            for line in f.read_text(encoding="utf-8").splitlines():
                parts = line.split()
                if parts:
                    counter[int(parts[0])] += 1

    if not counter:
        print("  [WARN] 未统计到任何标注")
        return

    ids = sorted(counter)
    labels = [f"{i}: {names[i] if i < len(names) else '?'}" for i in ids]
    counts = [counter[i] for i in ids]

    fig, ax = plt.subplots(figsize=(10, 5))
    colors = get_cmap(len(names))
    ax.bar(labels, counts, color=[colors(i) for i in ids])
    ax.set_title("Class Distribution (train+val+test)")
    ax.set_ylabel("Annotations")
    ax.tick_params(axis="x", rotation=30)
    for x, c in zip(range(len(ids)), counts):
        ax.text(x, c, str(c), ha="center", va="bottom", fontsize=8)
    fig.tight_layout()
    out = ROOT / "assets" / "class_distribution.png"
    fig.savefig(out, dpi=120, bbox_inches="tight")
    plt.close(fig)
    print(f"  [OK] 已保存: {out}")
    for i, c in zip(ids, counts):
        print(f"    {i}: {names[i] if i < len(names) else '?'} = {c}")


# ---------------------------------------------------------------------------
# 4. 数据质量检查
# ---------------------------------------------------------------------------
def quality_check(cfg: dict, data_root: Path, names: list, nc: int):
    print("=" * 60)
    print("4. 数据质量检查")
    print("=" * 60)
    total_img = total_lbl = 0

    for split in ("train", "val", "test"):
        rel = cfg.get(split)
        if not rel:
            continue
        img_dir = data_root / rel / "images"
        lbl_dir = data_root / rel / "labels"
        if not img_dir.exists():
            print(f"  [ERROR] 缺失图片目录: {img_dir}")
            continue

        img_files = {p.stem: p for p in img_dir.iterdir()}
        lbl_files = {p.stem: p for p in lbl_dir.glob("*.txt")} if lbl_dir.exists() else {}
        total_img += len(img_files)
        total_lbl += len(lbl_files)

        # a) 空 label 文件
        for stem, lp in lbl_files.items():
            if lp.stat().st_size == 0:
                PROBLEMS["empty_label"].append(str(lp))

        # b) 图片损坏（cv2 无法解码）
        for stem, ip in img_files.items():
            img = cv2.imread(str(ip))
            if img is None:
                PROBLEMS["corrupt_image"].append(str(ip))

        # c) 类别 ID 越界
        for stem, lp in lbl_files.items():
            for line in lp.read_text(encoding="utf-8").splitlines():
                parts = line.split()
                if parts and int(parts[0]) >= nc:
                    PROBLEMS["id_overflow"].append(f"{lp}: id={parts[0]}")

        # d) 图片与 label 不匹配（缺 label 的图片 / 无图片的 label）
        for stem in img_files.keys() - lbl_files.keys():
            PROBLEMS["unmatched"].append(f"{rel}: image {stem} 缺标注")
        for stem in lbl_files.keys() - img_files.keys():
            PROBLEMS["unmatched"].append(f"{rel}: label {stem} 无对应图片")

        print(f"  [{split}] 图片 {len(img_files)} / 标注 {len(lbl_files)}")

    print(f"\n  检查结果（全数据集，共 {total_img} 图 / {total_lbl} 标注文件）:")
    if total_lbl == 0:
        PROBLEMS["unmatched"].append("整个数据集没有任何标注文件！")

    checks = [
        ("空 label 文件", "empty_label"),
        ("损坏图片", "corrupt_image"),
        ("类别 ID 越界", "id_overflow"),
        ("图片/标注不匹配", "unmatched"),
    ]
    all_ok = True
    for title, key in checks:
        items = PROBLEMS[key]
        if items:
            all_ok = False
            print(f"    [[FAIL]] {title}: {len(items)} 处")
            for it in items[:5]:
                print(f"         - {it}")
            if len(items) > 5:
                print(f"         ... 其余 {len(items) - 5} 处省略")
        else:
            print(f"    [[OK]] {title}: 0 处")
    return all_ok


def main() -> int:
    parser = argparse.ArgumentParser(description="Construction Site Safety 数据集检查")
    parser.add_argument("--data", default=str(DEFAULT_YAML),
                        help="data.yaml 路径（默认 datasets/css-data/data.yaml）")
    parser.add_argument("--seed", type=int, default=42, help="随机种子（默认 42）")
    args = parser.parse_args()

    yaml_path = Path(args.data)
    if not yaml_path.exists():
        print(f"[ERROR] 未找到 {yaml_path}")
        print("        请先运行 python download_dataset.py 下载数据集，")
        print("        或用 --data 指定正确的 data.yaml 路径。")
        return 1

    cfg = read_yaml(yaml_path)
    data_root = (yaml_path.parent / cfg.get("path", ".")).resolve()
    names = names_to_list(cfg.get("names", []))
    nc = int(cfg.get("nc", len(names)))

    report_basic(cfg, data_root)
    draw_preview(cfg, data_root, names, args.seed)
    draw_distribution(cfg, data_root, names)
    ok = quality_check(cfg, data_root, names, nc)

    print("\n" + "=" * 60)
    if ok:
        print("[OK] 数据质量检查全部通过，可直接进入下一步：")
        print("     python prepare_data.py    # 精简为 5 个核心类别并生成 data_final.yaml")
    else:
        print("[WARN] 发现数据问题，请根据上方列表修复后重跑本脚本。")
    print("=" * 60)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
