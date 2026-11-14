# YOLO11s 工地安全帽 / 反光衣检测 —— 训练环境搭建指南

> 面向 Ubuntu 20.04/22.04 + NVIDIA GPU（CUDA 12.x 驱动）的从零训练环境搭建方案：
> 一键脚本（conda + Python 3.12 + PyTorch cu121 + Ultralytics YOLO11）+ 环境健康检查 + 排障手册。

---

## 一、目录结构与文件说明

```
ai-safety-service/
├── README.md            # 本指南
├── requirements.txt     # 依赖清单（setup 脚本读取）
├── data_final.yaml      # 5 类训练数据配置（由 prepare_data.py 生成）
├── setup_env.sh         # Linux 一键搭建环境
├── setup_env.ps1        # Windows 一键搭建环境
├── scripts/             # 全部工作流脚本
│   ├── download_dataset.py   # 从 Roboflow 下载数据集
│   ├── inspect_dataset.py    # 数据体检/可视化（输出到 assets/）
│   ├── prepare_data.py       # 精简为 5 类，生成 data_final.yaml
│   ├── train_stage1.py       # 阶段一：640 分辨率基线
│   ├── train_stage2.py       # 阶段二：960 分辨率小目标微调
│   ├── train_all.sh          # Linux 一键两阶段训练
│   ├── eval_test.py          # 测试集评估 + 预测可视化
│   ├── export_model.py       # 导出 ONNX/TorchScript + benchmark
│   └── verify_env.py         # 环境健康检查
├── docs/                # 说明文档（DATA_PREPARATION.md / TRAINING_MONITOR.md）
├── assets/              # 数据集可视化预览（可重新生成，不入库）
├── weights/             # 预训练权重（yolo11s.pt，不入库）
├── datasets/            # 数据集（不入库）
├── models/              # 部署产物（不入库）
└── runs/                # 训练输出与评估结果（不入库）
```

| 文件 | 作用 |
| --- | --- |
| `setup_env.sh` | **Linux** 一键搭建脚本：检测/安装 Miniconda → 创建 `site-ai` 环境（Python 3.12）→ 安装 PyTorch（cu121）→ 安装 ultralytics 等依赖 → 自动验证 |
| `setup_env.ps1` | **Windows** 一键搭建脚本：基于项目内 `.venv`（Python 3.12）完成同样安装流程 |
| `requirements.txt` | 全部 Python 依赖清单（含注释与版本范围，Linux/Windows 通用） |
| `scripts/verify_env.py` | 环境健康检查：CUDA/GPU/依赖版本/YOLO11s GPU 推理链路，输出 ✅/❌ 报告（跨平台） |
| `README.md` | 本指南：从裸机到训练就绪的完整步骤与常见问题排查 |

---

## 二点五、Windows 本机快速开始

> 适用：本机有 NVIDIA GPU（驱动支持 CUDA 12.x，`nvidia-smi` 可正常显示），
> 想直接在 Windows 上训练，无需服务器。

**前置条件**：已安装 Python 3.12（安装时勾选 **Add python.exe to PATH**），NVIDIA 驱动 ≥ 525。

```powershell
cd f:\summer_practice\build-sec\ai-safety-service

# 一键搭建（PowerShell 中执行；会自动创建 .venv 并安装全部依赖，约 10~20 分钟）
.\setup_env.ps1
```

脚本完成后，后续每次使用先激活环境：
```powershell
.\.venv\Scripts\Activate.ps1
python scripts\verify_env.py  # 健康检查，输出 ✅/❌ 报告
```

两阶段训练（推荐，一键依次执行）：
```powershell
.\.venv\Scripts\python.exe scripts\train_stage1.py   # 阶段一：640 基线
.\.venv\Scripts\python.exe scripts\train_stage2.py   # 阶段二：960 微调
```

> 训练脚本会自动按显存调整 batch（≤8G 用 8），无需手动指定。

> 若 PowerShell 提示"禁止运行脚本"，执行：`Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned`，或直接用 `powershell -ExecutionPolicy Bypass -File setup_env.ps1`。

---

## 二、环境要求

- **系统**：Ubuntu 20.04 / 22.04（x86_64）
- **GPU**：NVIDIA 显卡，建议显存 ≥ 8 GB（训练 YOLO11s 建议 ≥ 8~12 GB）
- **驱动**：NVIDIA 驱动 ≥ 525（`nvidia-smi` 中 `CUDA Version` 显示 12.x 即可）
  - 注意：**无需**单独安装 CUDA Toolkit，PyTorch 自带 CUDA 运行时
- **内存**：≥ 16 GB（建议 32 GB+）
- **磁盘**：≥ 50 GB 可用空间
- **网络**：可访问 GitHub / download.pytorch.org（国内可配置镜像，见 5.3）

---

## 三、快速开始（三步）

```bash
# ① 将 4 个文件上传到服务器同一目录（scp / git / 编辑器均可）

# ② 一键搭建（约 10~20 分钟，视网络而定）
bash setup_env.sh

# ③ 健康检查
conda activate site-ai
python scripts/verify_env.py
```

看到 **「全部检查通过」** 即可开始训练。

> 若在 Windows 上编辑过脚本，上传后请确保 `setup_env.sh` 为 LF 换行（CRLF 会导致脚本报 `\r` 错误）：
> ```bash
> sed -i 's/\r$//' setup_env.sh
> ```

---

## 四、从裸机到训练就绪：完整步骤清单

### 步骤 0：更新系统与基础工具
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y build-essential wget curl git

# OpenCV 运行依赖（否则 import cv2 会报 libGL.so.1 错误）
sudo apt install -y libgl1 libglib2.0-0
```

### 步骤 1：安装 NVIDIA 驱动
```bash
# 方式 A（推荐）：自动安装推荐版本驱动
sudo ubuntu-drivers autoinstall

# 方式 B：到 https://www.nvidia.com/drivers 下载 .run 安装包手动安装

# 安装完成后重启
sudo reboot

# 重启后验证：应能看到 GPU 型号与 CUDA Version 12.x
nvidia-smi
```

### 步骤 2：一键搭建环境
```bash
bash setup_env.sh
```
脚本将自动完成：
1. 检查系统与 NVIDIA 驱动
2. 检测 conda；若无则静默安装 Miniconda3 到 `~/miniconda3`
3. 创建 `site-ai` 环境（Python 3.12）
4. 安装 PyTorch（`--index-url https://download.pytorch.org/whl/cu121`，Python 3.12 下对应 torch 2.4.x）
5. 安装 ultralytics / opencv-python / pyyaml / roboflow / numpy / matplotlib（以 `requirements.txt` 为清单）
6. 自动运行验证命令

### 步骤 3：健康检查
```bash
conda activate site-ai
python scripts/verify_env.py
```
检查项：CUDA 可用性、GPU 型号与显存、nvidia-smi 驱动、各依赖版本、YOLO11s 推理链路。全部 ✅ 即可继续。

### 步骤 4：准备数据集

**方式 A：从 Roboflow 拉取（需先在 roboflow.com 注册并获取 API Key）**

使用项目脚本（需设置环境变量 `ROBOFLOW_API_KEY`，或 `--api-key` 参数）：
```bash
conda activate site-ai
python scripts/download_dataset.py
python scripts/inspect_dataset.py   # 数据体检 + 可视化（输出到 assets/）
python scripts/prepare_data.py      # 精简为 5 类，生成 data_final.yaml
```
也可直接使用 SDK：
```bash
python - <<'PY'
from roboflow import Roboflow
rf = Roboflow(api_key="YOUR_API_KEY")
project = rf.workspace("your-workspace").project("your-project")
dataset = project.version(1).download("yolov11")
PY
```

**方式 B：自制数据集（推荐）**，目录结构如下：
```
dataset/
├── data.yaml            # 数据集配置（必填）
├── images/
│   ├── train/           # 训练图片
│   └── val/             # 验证图片
└── labels/
    ├── train/           # 与图片同名的 .txt 标签
    └── val/
```

`data.yaml` 内容示例（以「安全帽 helmet、反光衣 vest」两类为例）：
```yaml
path: /path/to/dataset   # 数据集根目录
train: images/train
val: images/val
nc: 2
names:
  0: helmet
  1: vest
```

### 步骤 5：开始训练（两阶段策略）
```bash
conda activate site-ai

# 一键两阶段（640 基线 → 960 微调，自动按显存调 batch）
bash scripts/train_all.sh

# 或分步执行
python scripts/train_stage1.py    # 阶段一：640 快速收敛（epochs=100, batch 自动）
python scripts/train_stage2.py    # 阶段二：960 小目标微调（epochs=60, batch=8）
```
- 首次运行自动使用 `weights/yolo11s.pt`（已预置，无需下载）
- 训练日志与权重保存在 `runs/construction/yolo11s_stage{1,2}*/`
- 训练完成后自动验证并打印 mAP，对比结果写入 `runs/map_stage{1,2}.txt`
- 常用参数速查见「六、训练参数参考」

### 步骤 6：评估与导出模型
```bash
# 测试集完整评估 + 预测可视化
python scripts/eval_test.py

# 导出 ONNX / TorchScript + 推理 benchmark（自动复制部署权重到 models/）
python scripts/export_model.py

# 或用 CLI 直接导出
yolo export model=runs/construction/yolo11s_stage2_960/weights/best.pt format=onnx
```

---

## 五、常见问题排查

### 5.1 CUDA 不可用（`torch.cuda.is_available()` = False）

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| `nvidia-smi` 不存在 | 驱动未安装 | 重装驱动（见步骤 1） |
| `nvidia-smi` 显示 CUDA Version < 12.1 | 驱动版本过旧 | 升级驱动到 ≥ 525 |
| 驱动正常但 torch 报 False | 装成了 CPU 版 torch | `pip uninstall torch torchvision torchaudio` 后用 cu121 源重装 |
| WSL2 下报错 | WSL 未启用 GPU | Windows 侧装驱动 + `wsl --update`，在 WSL 内 `nvidia-smi` 验证 |
| 换 GPU / 换机器后仍 False | 环境损坏 | `conda env remove -n site-ai -y` 后重跑脚本 |

快速定位：
```bash
python -c "import torch; print(torch.__version__, torch.version.cuda, torch.cuda.is_available())"
```
若 `torch.__version__` 中**没有** `+cu121` 后缀，说明装错了版本。

### 5.2 显存不足（CUDA out of memory）

| 手段 | 操作 |
| --- | --- |
| 减小 batch | `batch=16 → 8 → 4` |
| 降低分辨率 | `imgsz=640 → 512 → 416` |
| 关闭缓存 | 去掉 `--cache` 或改为 `--cache ram` |
| 混合精度 | 默认已开启（AMP） |
| 换小模型 | `yolo11s` → `yolo11n` |
| 调整显存分配 | `export PYTORCH_CUDA_ALLOC_CONF=max_split_size_mb:128` |

用 `nvidia-smi -l 1` 实时观察显存占用。

### 5.3 网络下载慢 / 失败

| 场景 | 解决 |
| --- | --- |
| pip 下载慢 | 使用清华镜像：`pip install -i https://pypi.tuna.tsinghua.edu.cn/simple 包名` |
| torch cu121 包太大（约 2.6 GB） | 在其他机器/浏览器下载对应 wheel 后 `pip install ./torch-*.whl` |
| conda 下载慢 | 配置 `~/.condarc` 使用清华 conda 镜像 |
| yolo11s.pt 下载失败 | 手动下载放到工作目录（GitHub: ultralytics/assets），或用代理 |
| 下载中断 | `wget -c` 断点续传或使用 aria2 |

### 5.4 其他常见问题

| 报错 | 解决 |
| --- | --- |
| `libGL.so.1: cannot open shared object file` | `sudo apt install -y libgl1 libglib2.0-0` |
| `conda activate` 不生效 | `source ~/miniconda3/etc/profile.d/conda.sh && conda init bash`，重开终端 |
| 依赖版本冲突（如 numpy.core 报错） | 删除并重建 `site-ai` 环境后重跑脚本 |
| 训练时 `Permission denied` / 磁盘满 | `df -h` 检查磁盘，清理或扩容 |
| 中文路径 / 中文文件名导致读取失败 | 数据集统一使用英文命名 |
| 新开终端找不到 conda | 将 `export PATH="$HOME/miniconda3/bin:$PATH"` 写入 `~/.bashrc` |

---

## 六、训练参数参考

| 参数 | 说明 | 建议值 |
| --- | --- | --- |
| `model` | 基础模型 | `yolo11s.pt`（也可 `yolo11n.pt` 更快） |
| `epochs` | 训练轮数 | 100~300 |
| `imgsz` | 输入分辨率 | 640 |
| `batch` | 批大小（按显存调整） | 16（8GB 显存用 8） |
| `patience` | 早停耐心值 | 50 |
| `optimizer` | 优化器 | `auto`（默认） |
| `device` | 训练设备 | `0`（单卡）/ `0,1`（多卡） |
| `workers` | 数据加载进程数 | 8 |
| `seed` | 随机种子 | 42 |
| `pretrained` | 使用预训练权重 | `True`（默认） |

---

## 七、附录

### conda 常用命令
```bash
conda env list                              # 查看环境
conda activate site-ai                      # 激活
conda env remove -n site-ai -y              # 删除环境
conda create -n site-ai python=3.12 -y      # 手动创建
```

### 相关资源
- Ultralytics 官方文档：https://docs.ultralytics.com
- PyTorch 历史版本安装：https://pytorch.org/get-started/previous-versions
- NVIDIA 驱动下载：https://www.nvidia.com/drivers

祝训练顺利！

---

## 八、实时监测服务（FastAPI 部署）

> 本节面向「训练完成后，将模型部署为 7×24 实时监测服务」的场景。
> 服务从 RTSP/RTMP/本地视频流实时检测安全违规（未戴安全帽、未穿反光衣、
> 吸烟、明火等），命中后自动截图，并通过 HTTP POST 推送告警到 Java Spring 后端。

### 8.1 目录结构

```
ai-safety-service/
├── app/                      # 服务源码包
│   ├── main.py               # FastAPI 入口 + lifespan（启动/停止全部摄像头）
│   ├── config.py             # 全局配置（环境变量 + 默认值）
│   ├── detector.py           # YOLO11 检测器单例（GPU 共享，半精度）
│   ├── monitor.py            # 单路摄像头监测线程（核心循环）
│   ├── camera_manager.py     # 多路摄像头管理（单例）
│   ├── backend_sync.py       # 后端摄像头同步器（拉取后端列表自动启停）
│   ├── http_publisher.py     # 告警推送 + 失败落盘 + 周期重试
│   ├── health_check.py       # Java 后端在线探测（带缓存）
│   ├── models/schemas.py     # Pydantic 请求/响应模型
│   └── utils/                # 帧缓冲 / 截图 / 防抖 / ROI 过滤
├── configs/
│   └── camera_01.yaml        # 摄像头配置示例（每路一个 yaml）
├── models/                   # 部署用 .pt 模型（不入库）
├── screenshots/              # 截图输出（不入库；默认已迁移至 build-sec/ai_image/，
│                             #   见下方 8.2 说明，本目录仅作历史/兜底）
├── pending_alerts/           # 推送失败缓冲（不入库）
└── tests/
    ├── mock_java_server.py   # 模拟 Java 后端的 Flask 服务（含摄像头列表接口）
    ├── test_with_video.py    # 本地 mp4 全流程测试
    └── test_backend_sync.py  # 后端摄像头同步器测试（无需 GPU/模型）
```

### 8.2 安装与启动

```bash
# 安装依赖（torch 请按上文章节 1 用 cu121 源单独安装）
pip install -r requirements.txt

# ① 将训练好的权重复制到部署路径
cp runs/construction/yolo11s_stage2_960/weights/best.pt models/construction_safety_v1.pt

# ② 编辑 configs/camera_01.yaml 填入真实 RTSP 地址与 Java 端点

# ③ 启动服务（开发模式，二者等价）
python -m app.main
uvicorn app.main:app --host 0.0.0.0 --port 9000 --workers 1
```

服务启动时自动读取 `configs/*.yaml` 并启动全部摄像头，关闭时自动全部停止。

### 8.3 摄像头配置（configs/camera_01.yaml）

```yaml
camera_id: "CAM_01"
camera_name: "1号塔吊摄像头"
rtsp_url: "rtsp://admin:password@192.168.1.100:554/stream1"
location_name: "A区主体施工层"
model_path: "models/construction_safety_v1.pt"   # 缺省用全局配置
detect_interval: 3          # 每 3 帧检测一次
conf_threshold: 0.25
iou_threshold: 0.45
cooldown_seconds: 15        # 同类违规防抖冷却（秒）
debounce_mode: "global"     # 防抖模式: global=同类型全局冷却(防刷屏) / grid=位置网格(x,y各50px)
screenshot_dir: "CAM_01"    # 相对路径基于全局截图根目录 screenshots/
screenshot_base_url: "http://AI机器IP:9000/screenshots/CAM_01"
java_endpoint: "http://Java机器IP:8080/api/alerts/receive"
enabled_classes:
  - "NO-Hardhat"
  - "NO-Safety Vest"
  - "smoking"
roi_polygon: null           # 可选：[[x1,y1],[x2,y2],...]
```

### 8.4 管理 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/cameras/start` | 动态启动一路摄像头（接收 JSON 配置） |
| POST | `/api/cameras/stop/{camera_id}` | 停止指定摄像头 |
| GET | `/api/cameras/status` | 所有摄像头状态：state/FPS/告警计数/最后告警时间 |
| GET | `/api/cameras/{camera_id}/replay` | 该摄像头最近 10 条告警缩略信息 |
| GET | `/api/health` | 服务健康：GPU/模型加载/运行摄像头数 |
| GET | `/screenshots/{camera_id}/{filename}` | 截图静态文件访问 |

### 8.5 告警 JSON（推送 Java 的 body）

```json
{
  "event_id": "CAM_01_20260814_103000_NO-Hardhat",
  "camera_id": "CAM_01",
  "camera_name": "1号塔吊摄像头",
  "location_name": "A区主体施工层",
  "violation_type": "NO-Hardhat",
  "confidence": 0.87,
  "severity": "high",
  "timestamp": "2026-08-14T10:30:00.123456",
  "screenshot_filename": "NO-Hardhat_20260814_103000.jpg",
  "screenshot_url": "http://AI机器IP:9000/screenshots/CAM_01/NO-Hardhat_20260814_103000.jpg",
  "bbox": {"x1": 100, "y1": 200, "x2": 300, "y2": 500}
}
```

severity 规则：`conf >= 0.8 → high`，`>= 0.6 → medium`，否则 `→ low`。

### 8.6 可靠性与异常处理

- **断流重连**：断开后 3 秒重试，最多 10 次后进入 `error` 状态并轮询恢复；
- **防抖**：默认 `global` 模式——冷却期内同类型违规全局只触发一次（防刷屏）；`grid` 模式按位置网格（x//50, y//50）区分不同目标；
- **推送失败**：超时 5 秒，失败落盘 `pending_alerts/{event_id}.json`，后台每 30 秒重发；
- **Java 不可达**：健康检查（`{endpoint去掉/receive}/health`，2 秒超时）非 200 时直接落盘不请求；
- **兜底**：视频断开、推理异常、推送异常均被捕获，不会导致监测线程崩溃。

### 8.7 本地无 RTSP 测试

```bash
# 终端 1：启动模拟 Java 后端（Flask，端口 8001）
python tests/mock_java_server.py --port 8001

# 终端 2：用本地 mp4 跑检测+截图+推送全流程
python tests/test_with_video.py --video path/to/video.mp4 \
    --config configs/camera_01.yaml --max-frames 300

# 只检测截图不推送
python tests/test_with_video.py --video path/to/video.mp4 --no-push
```

### 8.8 常用环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_MODEL_PATH` | `models/construction_safety_v1.pt` | 全局模型路径 |
| `AI_DEVICE` | `auto` | `auto/cuda/cpu` |
| `AI_HALF` | `true` | 半精度推理（GPU） |
| `AI_CONFIGS_DIR` | `configs` | 摄像头配置目录 |
| `AI_SCREENSHOT_ROOT` | `../ai_image`（build-sec 根目录） | 截图根目录，默认落在 `build-sec/ai_image/{camera_id}/`，后端经 `/api/ai_image/**` 静态访问 |
| `AI_PENDING_DIR` | `pending_alerts` | 推送失败缓冲目录 |
| `AI_PORT` | `9000` | 服务端口 |
| `AI_RETRY_INTERVAL` | `30` | 堆积告警重发间隔（秒） |
| `AI_MAX_RECONNECT` | `10` | 断流最大重连次数 |
| `AI_RECONNECT_DELAY` | `3` | 重连间隔（秒） |
| `AI_HEALTH_TIMEOUT` | `2` | Java 健康检查超时（秒） |
| `AI_HTTP_TIMEOUT` | `5` | 告警推送超时（秒） |

### 8.9 后端摄像头同步（自动从 Java 后端获取视频流）

默认服务从 `configs/*.yaml` 读取摄像头配置。若希望「AI 从后端注册的摄像头
列表自动获取视频流」（与后端 `biz_camera` 表保持一致，无需手工维护 yaml），
可开启后端摄像头同步：

```powershell
# Windows（PowerShell）
$env:AI_BACKEND_SYNC_ENABLED = "true"
$env:AI_BACKEND_SYNC_BASE_URL = "http://127.0.0.1:8080/api"
python -m app.main
```

```bash
# Linux
export AI_BACKEND_SYNC_ENABLED=true
export AI_BACKEND_SYNC_BASE_URL=http://127.0.0.1:8080/api
python -m app.main
```

工作方式（`app/backend_sync.py`）：
- 启动时立即同步一次，之后每 `AI_BACKEND_SYNC_INTERVAL`（默认 60 秒）
  拉取一次 `GET {base}/video/cameras`（自动翻页取全量）；
- 有 `streamUrl` 的摄像头 -> 本地自动启动一路监测（`camera_id=BE-{id}`，
  截图存 `screenshots/BE-{id}/`）；
- 后端删除/停用某摄像头 -> 本地对应监测自动停止；
- 若 `configs/*.yaml` 已启动一路且 `backend_camera_id` 与后端 id 相同，
  则跳过重复启动（以 yaml 实例为准）；
- 后端返回空列表时跳过本次同步，避免误删本地监测；
- 后端鉴权开启时复用 `AI_BACKEND_USERNAME/PASSWORD` 登录，401 自动重登重试。

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_BACKEND_SYNC_ENABLED` | `false` | 同步开关 |
| `AI_BACKEND_SYNC_BASE_URL` | 空 | 后端 base URL（含 context-path），如 `http://127.0.0.1:8080/api` |
| `AI_BACKEND_SYNC_INTERVAL` | `60` | 同步周期（秒） |
| `AI_BACKEND_SYNC_CONF` | `0.25` | 默认置信度阈值 |
| `AI_BACKEND_SYNC_IOU` | `0.45` | 默认 IoU |
| `AI_BACKEND_SYNC_DETECT_INTERVAL` | `3` | 默认检测间隔（帧） |
| `AI_BACKEND_SYNC_COOLDOWN` | `15` | 默认告警冷却（秒） |
| `AI_BACKEND_SYNC_CLASSES` | 空 | 默认启用类别（逗号分隔，空=全部） |
| `AI_BACKEND_SYNC_SCREENSHOT_HOST` | `127.0.0.1` | 截图外链使用的 AI 服务地址 |
| `AI_BACKEND_SYNC_LOGIN_ENDPOINT` | 空 | 登录接口覆盖（默认 `{base}/auth/login`） |
| `AI_BACKEND_SYNC_TIMEOUT` | `5` | 后端请求超时（秒） |

同步器逻辑测试（无需 GPU/模型）：
```powershell
.\.venv\Scripts\python.exe tests\test_backend_sync.py -v
```

模拟后端联调：`tests/mock_java_server.py` 已提供 `GET /api/video/cameras`
接口（默认空列表，测试/联调时修改模块内 `CAMERAS` 列表即可），可直接配合
同步器联调。
