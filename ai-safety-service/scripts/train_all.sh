#!/usr/bin/env bash
# -*- coding: utf-8 -*-
"""
train_all.sh — 一键两阶段训练（Linux 服务器，conda 环境 site-ai）

流程:
    1. 激活 conda 环境 site-ai
    2. 阶段一: python train_stage1.py (640 基线)
    3. 阶段二: python train_stage2.py (960 微调)
    4. 输出两阶段 mAP 对比表

安全机制:
    set -euo pipefail：任何一步失败立即退出并打印错误提示。

用法:
    bash train_all.sh

注意:
    Windows 本机训练请直接用 .venv 的 python 依次运行两个脚本:
        .venv\\Scripts\\python.exe scripts\\train_stage1.py
        .venv\\Scripts\\python.exe scripts\\train_stage2.py
"""

set -euo pipefail

# 脚本所在目录（无论从哪里调用都能正确定位）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ---------- 工具函数 ----------
log()  { echo -e "[$(date '+%F %T')] $*"; }
line() { echo "================================================================"; }

fail() {
    echo -e "\n[ERROR] $*" >&2
    echo "[ERROR] 训练流程终止，请检查上方日志。" >&2
    exit 1
}

# ---------- 1. 激活 conda 环境 ----------
line
log "激活 conda 环境: site-ai"
if ! command -v conda >/dev/null 2>&1; then
    fail "未找到 conda 命令。请先安装 Miniconda 或 Anaconda，或手动 source conda.sh"
fi
# shellcheck disable=SC1090
source "$(conda info --base)/etc/profile.d/conda.sh"
conda activate site-ai || fail "conda activate site-ai 失败，请确认环境已创建 (setup_env.sh)"

# 环境自查
python - <<'PY'
import sys, torch
print(f"  Python: {sys.version.split()[0]}")
print(f"  torch : {torch.__version__} | CUDA available: {torch.cuda.is_available()}")
if torch.cuda.is_available():
    print(f"  GPU   : {torch.cuda.get_device_name(0)} ({torch.cuda.get_device_properties(0).total_memory/1024**3:.0f} GB)")
from ultralytics import YOLO
print(f"  ultralytics: {YOLO.__version__ if hasattr(YOLO, '__version__') else 'installed'}")
PY

# ---------- 2. 阶段一 ----------
line
log "阶段一开始: 640 分辨率快速收敛基线"
log "命令: python train_stage1.py"
line
if ! python train_stage1.py; then
    fail "阶段一训练失败，请检查数据配置 data_final.yaml 与显存设置"
fi
log "阶段一完成"

# ---------- 3. 阶段二 ----------
line
log "阶段二开始: 960 分辨率小目标微调"
log "命令: python train_stage2.py"
line
if ! python train_stage2.py; then
    fail "阶段二训练失败，请检查阶段一权重是否生成"
fi
log "阶段二完成"

# ---------- 4. 输出 mAP 对比表 ----------
line
log "两阶段训练全部完成，mAP 对比:"
RUNS_DIR="$SCRIPT_DIR/../runs"
MAP1="$RUNS_DIR/map_stage1.txt"
MAP2="$RUNS_DIR/map_stage2.txt"
if [[ -f "$MAP1" && -f "$MAP2" ]]; then
    S1_50=$(sed -n '1p' "$MAP1")
    S1_95=$(sed -n '2p' "$MAP1")
    S2_50=$(sed -n '1p' "$MAP2")
    S2_95=$(sed -n '2p' "$MAP2")
    printf "  %-16s %-8s %-12s %-12s\n" "阶段" "imgsz" "mAP@0.5" "mAP@0.5:0.95"
    printf "  %-16s %-8s %-12s %-12s\n" "Stage1(640)" "640" "$S1_50" "$S1_95"
    printf "  %-16s %-8s %-12s %-12s\n" "Stage2(960)" "960" "$S2_50" "$S2_95"
    # 简单计算 mAP@0.5 提升（bc 不存在时降级为整数比较提示）
    if command -v bc >/dev/null 2>&1; then
        DELTA=$(echo "$S2_50 - $S1_50" | bc)
        echo "  mAP@0.5 提升: $DELTA"
    fi
else
    log "[WARN] 未找到 runs/map_stage1.txt 或 runs/map_stage2.txt，跳过对比输出"
fi
line
log "最佳权重:"
[[ -f "$RUNS_DIR/best_stage1.txt" ]] && log "  Stage1: $(cat "$RUNS_DIR/best_stage1.txt")"
[[ -f "$RUNS_DIR/best_stage2.txt" ]] && log "  Stage2: $(cat "$RUNS_DIR/best_stage2.txt")"
log "后续导出: python export_model.py"
line
exit 0
