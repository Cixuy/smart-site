#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
download_dataset.py — 从 Roboflow 下载 Construction Site Safety 数据集（10 类）

功能:
    1. 使用 Roboflow Python SDK 下载项目 roboflow-universe-projects/construction-site-safety
    2. 导出格式优先 yolov11，失败自动回退 yolov8（二者均兼容 YOLO11）
    3. 下载到 ./datasets/css-data/
    4. API Key 来源: 环境变量 ROBOFLOW_API_KEY 或 --api-key 参数
    5. 本地已存在数据集（含 data.yaml）时自动跳过下载

用法:
    # 方式一: 环境变量（推荐，key 不进入 shell 历史）
    #   Linux/macOS : export ROBOFLOW_API_KEY="你的key"
    #   Windows     : $env:ROBOFLOW_API_KEY = "你的key"
    python download_dataset.py [--version 1]

    # 方式二: 命令行参数
    python download_dataset.py --api-key 你的key

    版本号查看: 打开数据集网页，右上角版本信息（如 "v1"、"v2"）即为版本号。

输出:
    datasets/css-data/
        ├── data.yaml        # Roboflow 自动生成的配置（10 类）
        ├── README.txt
        ├── train/           # 训练集 (images/ + labels/)
        ├── valid/           # 验证集 (images/ + labels/)
        └── test/            # 测试集 (images/ + labels/)
"""

import argparse
import os
import sys
from pathlib import Path

# Windows 控制台默认 GBK，强制 UTF-8 避免中文打印乱码/报错
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

ROOT = Path(__file__).resolve().parent.parent  # 项目根目录（本脚本位于 scripts/ 子目录）
DATA_DIR = ROOT / "datasets" / "css-data"

WORKSPACE = "roboflow-universe-projects"
PROJECT = "construction-site-safety"


def print_tree(directory: Path, max_depth: int = 3) -> None:
    """打印目录树（最多 max_depth 层，用于展示数据集结构）。"""
    if not directory.exists():
        return
    for root, dirs, files in os.walk(directory):
        depth = root[len(str(directory)):].count(os.sep)
        if depth >= max_depth:
            dirs[:] = []
            continue
        indent = "  " * depth
        name = Path(root).name if depth > 0 else str(directory)
        print(f"{indent}{name}/")
        for f in sorted(files)[:6]:
            size = (Path(root) / f).stat().st_size
            print(f"{indent}  {f} ({size / 1024:.0f} KB)")
        if len(files) > 6:
            print(f"{indent}  ... 共 {len(files)} 个文件")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="下载 Construction Site Safety 数据集（Roboflow，10 类）",
    )
    parser.add_argument("--api-key", default=os.environ.get("ROBOFLOW_API_KEY", ""),
                        help="Roboflow API Key（也可用环境变量 ROBOFLOW_API_KEY）")
    parser.add_argument("--version", type=int, default=1,
                        help="数据集版本号（默认 1，可在网页右上角确认）")
    parser.add_argument("--format", default="yolov11",
                        choices=["yolov11", "yolov8"],
                        help="导出格式（默认 yolov11）")
    args = parser.parse_args()

    # ------------------------------------------------------------------
    # 1. 检查 API Key
    # ------------------------------------------------------------------
    if not args.api_key:
        print("[ERROR] 未提供 Roboflow API Key。")
        print("        两种方式任选其一：")
        print("          - 设置环境变量:  $env:ROBOFLOW_API_KEY = \"你的key\"  (Windows)")
        print("                             export ROBOFLOW_API_KEY=\"你的key\" (Linux/macOS)")
        print("          - 命令行传参:    python download_dataset.py --api-key 你的key")
        print("       获取 Key：登录 https://universe.roboflow.com -> 头像 -> Settings -> "
              "API Keys")
        return 1

    # ------------------------------------------------------------------
    # 2. 已有本地数据集则跳过
    # ------------------------------------------------------------------
    if DATA_DIR.exists() and (DATA_DIR / "data.yaml").exists():
        print(f"[INFO] 检测到本地数据集已存在：{DATA_DIR}（含 data.yaml），跳过下载。")
        print("[INFO] 若需重新下载，请先删除该目录：")
        print(f"       PowerShell:  Remove-Item -Recurse -Force {DATA_DIR}")
        print("\n[INFO] 当前目录结构：")
        print_tree(DATA_DIR)
        return 0

    # ------------------------------------------------------------------
    # 3. 下载
    # ------------------------------------------------------------------
    print(f"[INFO] 开始下载项目 {WORKSPACE}/{PROJECT} 版本 v{args.version} ...")
    print(f"[INFO] 目标目录: {DATA_DIR}")
    print(f"[INFO] 导出格式: {args.format}")

    try:
        from roboflow import Roboflow
    except ImportError:
        print("[ERROR] 未安装 roboflow SDK，请先执行: pip install roboflow")
        return 1

    try:
        rf = Roboflow(api_key=args.api_key)
        project = rf.workspace(WORKSPACE).project(PROJECT)
        version = project.version(args.version)

        fmt = args.format
        try:
            version.download(fmt, location=str(DATA_DIR))
        except Exception as exc:  # noqa: BLE001 —— yolov11 可能不被旧版 SDK 支持
            if fmt == "yolov11":
                print(f"[WARN] yolov11 格式下载失败（{exc}），回退到 yolov8 ...")
                version.download("yolov8", location=str(DATA_DIR))
            else:
                raise
    except Exception as exc:  # noqa: BLE001
        print(f"[ERROR] 下载失败: {exc}")
        print("        请检查：① API Key 是否正确 ② 版本号 v{args.version} 是否存在 "
              "③ 网络是否能访问 Roboflow")
        return 1

    # ------------------------------------------------------------------
    # 4. 结果确认
    # ------------------------------------------------------------------
    if not (DATA_DIR / "data.yaml").exists():
        print("[ERROR] 下载完成但未找到 data.yaml，数据集可能不完整，请检查网络后重试。")
        return 1

    print("\n[OK] 数据集下载完成！目录结构：")
    print_tree(DATA_DIR)
    print(f"\n[INFO] 下一步：运行 python inspect_dataset.py 检查数据质量")
    return 0


if __name__ == "__main__":
    sys.exit(main())
