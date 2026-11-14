# =============================================================================
# setup_env.ps1 — Windows 本机 YOLO11s 训练环境一键搭建脚本（PowerShell）
#
# 适用环境 : Windows 10/11 x64 + NVIDIA GPU（已安装支持 CUDA 12.x 的驱动）
# 目标环境 : 项目内 .venv（Python 3.12）/ PyTorch(cu121) / Ultralytics YOLO11
# 用法     : powershell -ExecutionPolicy Bypass -File setup_env.ps1
#            或直接在项目目录: .\setup_env.ps1
#
# 说明: 与 Linux 版 setup_env.sh 对应。依赖清单复用同目录 requirements.txt，
#       health check 复用 verify_env.py（二者均为跨平台实现）。
# =============================================================================

$ErrorActionPreference = "Stop"

# ----------------------------- 控制台输出工具 -------------------------------
function Write-Step { Write-Host ""; Write-Host ("=" * 70); Write-Host ("  " + $args[0]) -ForegroundColor Cyan; Write-Host ("=" * 70) }
function Write-Info { Write-Host "[INFO] $args" -ForegroundColor Green }
function Write-Warn { Write-Host "[WARN] $args" -ForegroundColor Yellow }
function Write-Fail { Write-Host "[ERROR] $args" -ForegroundColor Red }

function Assert-LastExit {
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "上一条命令失败（退出码 $LASTEXITCODE）。请检查上方输出，常见问题见 README.md【五、常见问题排查】。"
        exit 1
    }
}

# 工作目录 = 脚本所在目录（保证路径正确，避免依赖当前 PowerShell 位置）
$Root = $PSScriptRoot
Set-Location $Root

# ------------------------------ 1. 系统检查 --------------------------------
Write-Step "步骤 1/6：检查 NVIDIA 驱动与 Python"

$smi = Get-Command nvidia-smi -ErrorAction SilentlyContinue
if ($smi) {
    Write-Info "检测到 NVIDIA 驱动："
    nvidia-smi --query-gpu=name,driver_version,memory.total --format=csv | ForEach-Object { Write-Host ("    " + $_) }
} else {
    Write-Warn "未找到 nvidia-smi —— 请先到 https://www.nvidia.cn/drivers 安装 NVIDIA 驱动（需支持 CUDA 12.x）。"
    Write-Warn "脚本会继续安装 Python 环境，但 CUDA 训练前必须解决驱动。"
}

$PythonCmd = Get-Command python -ErrorAction SilentlyContinue
if (-not $PythonCmd) {
    Write-Fail "未找到 python。请安装 Python 3.12（安装时勾选 Add to PATH）：https://www.python.org/downloads/"
    exit 1
}
$PyVer = & python -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}')"
if ($LASTEXITCODE -ne 0) { Write-Fail "python 执行失败"; exit 1 }
Write-Info "系统 Python：$PyVer @ $(& python -c "import sys; print(sys.executable)")"

# ------------------------------ 2. 虚拟环境 --------------------------------
Write-Step "步骤 2/6：准备虚拟环境 .venv（Python 3.12）"

$VenvPy = Join-Path $Root ".venv\Scripts\python.exe"
if (Test-Path $VenvPy) {
    Write-Info "检测到已有 .venv，直接复用（如需重建请先删除 .venv 目录）。"
} else {
    Write-Info "创建虚拟环境 .venv ..."
    & python -m venv .venv
    Assert-LastExit
    Write-Info ".venv 创建完成。"
}
Write-Info "venv Python 版本：$(& $VenvPy -c "import sys; print(sys.version.split()[0])")"

Write-Info "升级 pip / setuptools / wheel ..."
& $VenvPy -m pip install --upgrade pip setuptools wheel -q
Assert-LastExit

# ------------------------------ 3. 安装 PyTorch -----------------------------
Write-Step "步骤 3/6：安装 PyTorch（CUDA 12.1 版本，cu121 源）"

Write-Info "从 https://download.pytorch.org/whl/cu121 安装 torch/torchvision/torchaudio（约 2.6 GB，请耐心等待）..."
& $VenvPy -m pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu121
if ($LASTEXITCODE -ne 0) {
    Write-Fail "torch cu121 安装失败。常见原因：网络无法访问 download.pytorch.org。"
    Write-Fail "建议: ① 配置代理后重试  ② 用浏览器/下载工具先下载对应 wheel（见 README 5.3）"
    exit 1
}

Write-Info "PyTorch 安装结果："
& $VenvPy -c "import torch; print('    torch =', torch.__version__, '| cuda build =', torch.version.cuda)"

# ------------------------------ 4. 安装依赖 --------------------------------
Write-Step "步骤 4/6：安装 ultralytics / opencv / pyyaml / roboflow / numpy / matplotlib"

if (Test-Path (Join-Path $Root "requirements.txt")) {
    Write-Info "使用 requirements.txt 作为依赖清单安装..."
    & $VenvPy -m pip install -r (Join-Path $Root "requirements.txt")
} else {
    Write-Warn "未找到 requirements.txt，使用内置安装列表..."
    & $VenvPy -m pip install "ultralytics>=8.3" "opencv-python>=4.9" "pyyaml>=6.0.2" roboflow "numpy>=1.26" "matplotlib>=3.8"
}
Assert-LastExit

Write-Info "关键依赖安装结果："
& $VenvPy -c "import ultralytics, cv2, yaml, numpy, matplotlib; print(f'    ultralytics={ultralytics.__version__} opencv={cv2.__version__} yaml={yaml.__version__} numpy={numpy.__version__} matplotlib={matplotlib.__version__}')"

# ------------------------------ 5. 验证 --------------------------------
Write-Step "步骤 5/6：运行基础验证"

Write-Info "验证 torch.cuda + ultralytics 版本..."
& $VenvPy -c "import torch, ultralytics; print('    cuda_available =', torch.cuda.is_available(), '| gpu =', torch.cuda.get_device_name(0), '| ultralytics =', ultralytics.__version__)"
if ($LASTEXITCODE -ne 0) {
    Write-Warn "基础验证未通过（常见原因：CUDA 不可用时 get_device_name(0) 报错）。"
    Write-Warn "下一步运行 verify_env.py 查看详细报告。"
}

if (Test-Path (Join-Path $Root "verify_env.py")) {
    Write-Step "步骤 6/6：执行完整环境健康检查"
    $env:PYTHONIOENCODING = "utf-8"   # 避免 Windows GBK 控制台打印 ✅/❌ 报错
    & $VenvPy (Join-Path $Root "verify_env.py")
}

# ------------------------------ 完成 --------------------------------
Write-Step "全部完成！"

Write-Info "后续使用：先激活环境，再训练/检查"
Write-Host "    .\.venv\Scripts\Activate.ps1"
Write-Host "    python verify_env.py"
Write-Host "    yolo detect train data=data.yaml model=yolo11s.pt epochs=100 imgsz=640 batch=8"
