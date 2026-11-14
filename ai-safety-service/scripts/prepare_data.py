#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
prepare_data.py — 精简数据集为 5 个核心类别，生成 data_final.yaml

背景:
    Roboflow 下载的 Construction Site Safety 数据集有 10 个类别，其中 Mask/NO-Mask
    (口罩)、Safety Cone(锥桶)、machinery、vehicle 不是本项目的检测目标。本脚本：

    1. 读取 Roboflow 自动生成的 datasets/css-data/data.yaml
    2. 只保留 5 个核心类别，其余类别标注从 label 文件中过滤掉
       （保留: Hardhat / NO-Hardhat / Safety Vest / NO-Safety Vest / Person）
    3. 对保留的类别做 ID 重映射（原 10 类 ID -> 新 0~4 紧凑 ID）
    4. 重新生成 data_final.yaml，使用绝对路径（避免相对路径跨机器失效）

安全机制:
    过滤前会自动将原始 labels 目录备份为 <数据根>/labels_backup_10cls/，
    备份已存在时不会重复覆盖。

用法:
    python prepare_data.py [--data datasets/css-data/data.yaml]
                           [--out data_final.yaml]

输出:
    data_final.yaml                        # 训练直接用: data=data_final.yaml
    <数据根>/labels_backup_10cls/          # 原始 10 类标注备份
    （原 labels 目录内文件被原地重写为 5 类版本）

注意:
    Roboflow yolov8/yolov11 导出的验证集目录名为 "valid"（不是 "val"），
    脚本从 data.yaml 动态读取路径，无需硬编码。
"""

import argparse
import shutil
import sys
from pathlib import Path

import yaml

# Windows 控制台 UTF-8 兼容
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

ROOT = Path(__file__).resolve().parent.parent  # 项目根目录（本脚本位于 scripts/ 子目录）
DEFAULT_YAML = ROOT / "datasets" / "css-data" / "data.yaml"

# 保留的 5 个核心类别（新 ID -> 类别名）
KEEP_CLASSES = {
    "Hardhat": 0,
    "NO-Hardhat": 1,
    "Safety Vest": 2,
    "NO-Safety Vest": 3,
    "Person": 4,
}


def read_yaml(path: Path) -> dict:
    with open(path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def names_to_list(names) -> list:
    if isinstance(names, list):
        return list(names)
    if isinstance(names, dict):
        return [names[i] for i in range(len(names))]
    raise ValueError(f"无法解析 names: {type(names)}")


def split_rel(rel: str) -> str:
    """将 data.yaml 中 train/val/test 字段（Roboflow 常见 '../train/images'）
    归一化为相对数据集根目录的路径（如 'train/images'）。"""
    p = Path(rel)
    parts = [x for x in p.parts if x not in ("..", ".")]
    return "/".join(parts) if parts else str(p)


def labels_dir(data_root: Path, rel: str) -> Path:
    """定位某 split 的 labels 目录。
    rel 可能指向 images 目录（如 'train/images'）或 split 根目录（如 'train'），
    兼容两种情况；labels 与 images 同级（train/labels）。"""
    norm = split_rel(rel)
    split_root = data_root / norm
    if split_root.name == "images":
        split_root = split_root.parent
    return split_root / "labels"


def main() -> int:
    parser = argparse.ArgumentParser(description="精简数据集为 5 个核心类别")
    parser.add_argument("--data", default=str(DEFAULT_YAML),
                        help="Roboflow data.yaml 路径（默认 datasets/css-data/data.yaml）")
    parser.add_argument("--out", default="data_final.yaml",
                        help="输出配置文件（默认 data_final.yaml）")
    args = parser.parse_args()

    yaml_path = Path(args.data)
    if not yaml_path.exists():
        print(f"[ERROR] 未找到 {yaml_path}，请先运行 python download_dataset.py")
        return 1

    cfg = read_yaml(yaml_path)
    data_root = (yaml_path.parent / cfg.get("path", ".")).resolve()
    old_names = names_to_list(cfg.get("names", []))
    nc = int(cfg.get("nc", len(old_names)))

    # ------------------------------------------------------------------
    # 1. 建立 原ID -> 新ID 映射，并校验类别都存在
    # ------------------------------------------------------------------
    old_to_new = {}
    for old_id, name in enumerate(old_names):
        if name in KEEP_CLASSES:
            old_to_new[old_id] = KEEP_CLASSES[name]
    missing = set(KEEP_CLASSES) - set(old_names)
    if missing:
        print(f"[ERROR] data.yaml 中缺失保留类别: {missing}")
        print(f"        实际类别: {old_names}")
        return 1

    print("=" * 60)
    print(f"1. 类别映射（{nc} 类 -> {len(KEEP_CLASSES)} 类）")
    print("=" * 60)
    removed = [f"{i}:{n}" for i, n in enumerate(old_names) if i not in old_to_new]
    keep_items = sorted(KEEP_CLASSES.items(), key=lambda x: x[1])  # 按新 ID 排序
    print("  保留: " + ", ".join(
        f"{n} (原id {old_names.index(n)} -> 新id {new})" for n, new in keep_items)
    )
    print(f"  忽略: {', '.join(removed) if removed else '无'}")

    # ------------------------------------------------------------------
    # 2. 备份原始 labels
    # ------------------------------------------------------------------
    # Roboflow 的 yaml 字段为 "val: valid"，统一按 yaml 字段名遍历
    SPLITS = ("train", "val", "test")
    backup = data_root / "labels_backup_25cls"
    if not backup.exists():
        print(f"\n2. 备份原始标注 -> {backup}")
        for split in SPLITS:
            rel = cfg.get(split)
            if not rel:
                continue
            src = labels_dir(data_root, rel)
            if src.exists():
                dst = backup / split
                dst.mkdir(parents=True, exist_ok=True)
                for f in src.glob("*.txt"):
                    shutil.copy2(f, dst / f.name)
        print("  备份完成")
    else:
        print(f"\n2. [SKIP] 备份已存在: {backup}（不重复备份）")

    # ------------------------------------------------------------------
    # 3. 重写 labels（原地过滤 + ID 重映射）
    # ------------------------------------------------------------------
    print("\n3. 过滤并重映射标注")
    stats = {}  # 类别名 -> 过滤掉的标注数
    total_kept = 0

    for split in SPLITS:
        rel = cfg.get(split)
        if not rel:
            continue
        lbl_dir = labels_dir(data_root, rel)
        if not lbl_dir.exists():
            print(f"  [SKIP] {split}: 无 labels 目录 {lbl_dir}")
            continue

        n_files = n_kept = n_dropped = n_emptied = 0
        for lbl_file in lbl_dir.glob("*.txt"):
            n_files += 1
            new_lines = []
            for line in lbl_file.read_text(encoding="utf-8").splitlines():
                parts = line.split()
                if len(parts) < 5:
                    continue  # 丢弃格式错误的行
                old_id = int(parts[0])
                if old_id in old_to_new:
                    new_lines.append(f"{old_to_new[old_id]} " + " ".join(parts[1:]))
                else:
                    n_dropped += 1
                    name = old_names[old_id] if old_id < nc else f"id{old_id}"
                    stats[name] = stats.get(name, 0) + 1
            if new_lines:
                lbl_file.write_text("\n".join(new_lines) + "\n", encoding="utf-8")
                n_kept += len(new_lines)
            else:
                lbl_file.unlink()  # 全部被过滤 -> 删除空标注
                n_emptied += 1
        total_kept += n_kept
        print(f"  [{split}] 标注文件 {n_files} 个 | 保留标注 {n_kept} | "
              f"过滤标注 {n_dropped} | 清空删除 {n_emptied}")

    print(f"\n  过滤统计（被忽略类别的标注数）:")
    for name, cnt in sorted(stats.items(), key=lambda x: -x[1]):
        print(f"    {name}: {cnt}")
    print(f"  保留标注总数: {total_kept}")

    # ------------------------------------------------------------------
    # 4. 生成 data_final.yaml（绝对路径）
    # ------------------------------------------------------------------
    print("\n4. 生成 data_final.yaml（绝对路径 + 5 类）")
    new_names = {new: name for name, new in KEEP_CLASSES.items()}
    final_cfg = {
        "path": data_root.as_posix(),          # 绝对路径，跨机器需修改这里
        "train": split_rel(cfg.get("train")),  # 归一化，如 train/images
        "val": split_rel(cfg.get("val")),      # 如 valid/images
        "nc": len(KEEP_CLASSES),
        "names": new_names,
    }
    out_path = (ROOT / args.out).resolve()
    with open(out_path, "w", encoding="utf-8") as f:
        yaml.safe_dump(final_cfg, f, allow_unicode=True, sort_keys=False)

    print(f"  [OK] 已生成: {out_path}")
    print(f"  [OK] path  = {final_cfg['path']}")
    print(f"  [OK] train = {final_cfg['train']}")
    print(f"  [OK] val   = {final_cfg['val']}")
    print(f"  [OK] names = {new_names}")

    print("\n" + "=" * 60)
    print("下一步: 训练")
    print(f"  yolo detect train data={args.out} model=yolo11s.pt "
          f"epochs=100 imgsz=640 batch=8")
    print("=" * 60)
    return 0


if __name__ == "__main__":
    sys.exit(main())
