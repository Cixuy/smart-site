#!/usr/bin/env bash
# =============================================================================
# setup_env.sh — YOLO11s 工地安全帽/反光衣检测训练环境一键搭建脚本
#
# 适用系统 : Ubuntu 20.04 / 22.04 (x86_64) + NVIDIA GPU（CUDA 12.x 驱动）
# 目标环境 : conda 环境 site-ai / Python 3.12 / PyTorch(cu121) / Ultralytics YOLO11
# 用法     : bash setup_env.sh
#
# 脚本自动完成:
#   检测/安装 Miniconda3 -> 创建 site-ai 环境(py3.12) -> 安装 PyTorch(cu121)
#   -> 安装 ultralytics 等依赖 -> 自动运行验证
#
# 说明: 依赖以同目录 requirements.txt 为唯一清单; 若该文件缺失则退回内置安装列表。
# =============================================================================

set -euo pipefail

# ----------------------------- 颜色与日志工具 -------------------------------
COLOR_GREEN=$'\e[32m'
COLOR_YELLOW=$'\e[33m'
COLOR_RED=$'\e[31m'
COLOR_RESET=$'\e[0m'

info()  { echo -e "${COLOR_GREEN}[INFO]${COLOR_RESET}  $*"; }
warn()  { echo -e "${COLOR_YELLOW}[WARN]${COLOR_RESET}  $*"; }
error() { echo -e "${COLOR_RED}[ERROR]${COLOR_RESET} $*" >&2; }

# 任一步骤失败立即退出并给出提示（配合 set -e 使用）
on_error() {
    error "脚本在第 ${BASH_LINENO[0]} 行执行失败，请查看上方错误信息。"
    error "常见问题请参考同目录 README.md 的【五、常见问题排查】章节。"
    exit 1
}
trap on_error ERR

step() {
    echo
    echo "----------------------------------------------------------------------"
    echo ">>> $*"
    echo "----------------------------------------------------------------------"
}

# ------------------------------ 1. 系统检查 --------------------------------
step "步骤 1/6：检查系统环境"

if [ -f /etc/os-release ]; then
    # shellcheck disable=SC1091
    . /etc/os-release
    info "系统：${PRETTY_NAME:-$ID $VERSION_ID}（架构 $(uname -m)）"
else
    warn "未找到 /etc/os-release，无法确认发行版，脚本按 Ubuntu 兼容模式继续。"
fi

if command -v nvidia-smi >/dev/null 2>&1; then
    info "已检测到 NVIDIA 驱动，当前 GPU 信息："
    nvidia-smi --query-gpu=name,driver_version,memory.total --format=csv,noheader | sed 's/^/    /'
else
    warn "未检测到 nvidia-smi —— 请先安装 NVIDIA 驱动（需支持 CUDA 12.x，驱动版本 >= 525）。"
    warn "安装参考：sudo ubuntu-drivers autoinstall 后 reboot；或到 NVIDIA 官网下载驱动。"
    warn "脚本将继续安装 Python 环境，但训练前请务必解决驱动问题。"
fi

# ------------------------------ 2. conda --------------------------------
step "步骤 2/6：检测 / 安装 conda（Miniconda3）"

CONDA_BIN=""
if command -v conda >/dev/null 2>&1; then
    CONDA_BIN="$(command -v conda)"
elif [ -d "$HOME/miniconda3" ]; then
    CONDA_BIN="$HOME/miniconda3/bin/conda"
elif [ -d "$HOME/miniconda" ]; then
    CONDA_BIN="$HOME/miniconda/bin/conda"
elif [ -d "$HOME/anaconda3" ]; then
    CONDA_BIN="$HOME/anaconda3/bin/conda"
fi

if [ -n "$CONDA_BIN" ] && [ -x "$CONDA_BIN" ]; then
    info "找到 conda：$CONDA_BIN（$("$CONDA_BIN" --version | head -n1)）"
else
    info "未检测到 conda，开始下载并静默安装 Miniconda3（Linux x86_64）..."

    INSTALLER="/tmp/miniconda3-installer.sh"
    if command -v wget >/dev/null 2>&1; then
        wget -q --show-progress "https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh" -O "$INSTALLER"
    elif command -v curl >/dev/null 2>&1; then
        curl -fSL "https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh" -o "$INSTALLER"
    else
        error "未找到 wget 或 curl，请先执行：sudo apt-get install -y wget curl"
        exit 1
    fi

    info "运行 Miniconda3 安装器（安装到 $HOME/miniconda3，静默模式）..."
    bash "$INSTALLER" -b -p "$HOME/miniconda3"
    rm -f "$INSTALLER"

    CONDA_BIN="$HOME/miniconda3/bin/conda"
    export PATH="$HOME/miniconda3/bin:$PATH"
    info "Miniconda3 安装完成：$("$CONDA_BIN" --version | head -n1)"
fi

# 初始化 conda，使脚本内可直接使用 conda activate
CONDA_BASE="$("$CONDA_BIN" info --base)"
# shellcheck disable=SC1090
source "$CONDA_BASE/etc/profile.d/conda.sh"
info "conda 初始化完成（base 目录：$CONDA_BASE）"

# ------------------------------ 3. 创建环境 --------------------------------
step "步骤 3/6：创建 conda 环境 site-ai（Python 3.12）"

if "$CONDA_BIN" env list | grep -qE '^\s*site-ai\s'; then
    warn "环境 site-ai 已存在，将复用该环境。如需重建请先执行：conda env remove -n site-ai -y"
else
    info "创建环境 site-ai（python=3.12），首次运行需下载依赖，请耐心等待..."
    "$CONDA_BIN" create -n site-ai python=3.12 -y
    info "环境 site-ai 创建完成。"
fi

conda activate site-ai
info "已激活环境：$(python --version) @ $(command -v python)"

info "升级 pip / setuptools / wheel ..."
python -m pip install --upgrade pip setuptools wheel -q

# ------------------------------ 4. 安装 PyTorch -----------------------------
step "步骤 4/6：安装 PyTorch（CUDA 12.1 版本）"

info "使用官方 cu121 源安装 torch/torchvision/torchaudio（Python 3.12 下最高为 2.4.x，满足 >=2.1）..."
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu121

info "PyTorch 安装结果："
python -c "import torch; print('    torch =', torch.__version__, '| cuda build =', torch.version.cuda)"

# ------------------------------ 5. 安装依赖 --------------------------------
step "步骤 5/6：安装 ultralytics / opencv / pyyaml / roboflow / numpy / matplotlib"

if [ -f requirements.txt ]; then
    info "使用 requirements.txt 作为依赖清单安装..."
    pip install -r requirements.txt
else
    warn "未找到 requirements.txt，使用内置安装列表..."
    pip install "ultralytics>=8.3" "opencv-python>=4.9" "pyyaml>=6.0.2" roboflow "numpy>=1.26" "matplotlib>=3.8"
fi

info "关键依赖安装结果："
python - <<'PY'
import ultralytics, cv2, yaml, numpy, matplotlib
print(f"    ultralytics   = {ultralytics.__version__}")
print(f"    opencv-python = {cv2.__version__}")
print(f"    pyyaml        = {yaml.__version__}")
print(f"    numpy         = {numpy.__version__}")
print(f"    matplotlib    = {matplotlib.__version__}")
PY

# ------------------------------ 6. 验证 --------------------------------
step "步骤 6/6：验证 CUDA 与模型库"

info "运行基础验证（torch.cuda + ultralytics 版本）..."
if python -c "import torch, ultralytics; print('    cuda_available =', torch.cuda.is_available(), '| gpu =', torch.cuda.get_device_name(0), '| ultralytics =', ultralytics.__version__)"; then
    info "基础验证通过。"
else
    warn "基础验证命令执行失败（常见原因：CUDA 不可用时 get_device_name(0) 会报错）。"
    warn "请运行 python verify_env.py 查看详细健康报告。"
fi

if [ -f verify_env.py ]; then
    info "发现 verify_env.py，执行完整环境健康检查..."
    python verify_env.py || true
fi

# ------------------------------ 完成 --------------------------------
step "全部完成！"

info "激活环境并开始训练："
echo "    conda activate site-ai"
echo "    yolo detect train data=data.yaml model=yolo11s.pt epochs=100 imgsz=640"
info "建议先运行一次健康检查确认环境 OK：python verify_env.py"
