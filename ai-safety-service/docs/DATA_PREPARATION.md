# 数据集准备指南（Construction Site Safety，10 类）

本指南说明如何获取并准备「工地安全帽/反光衣检测」训练数据集，包括两条路径：
**有 Roboflow API Key（脚本自动下载）** 与 **无 Key（网页手动下载）**，以及
`data.yaml` 各字段的含义。

---

## 一、数据流水线总览

```
download_dataset.py  ──►  datasets/css-data/          （10 类，Roboflow 原始格式）
        │
inspect_dataset.py    ──►  dataset_preview.png        （5 张标注可视化）
        │                   class_distribution.png    （类别分布柱状图）
        │                   数据质量检查报告
        │
prepare_data.py       ──►  data_final.yaml            （5 个核心类别 + 绝对路径）
                           datasets/css-data/labels_backup_10cls/  （原始备份）
```

| 脚本 | 输入 | 输出 | 说明 |
| --- | --- | --- | --- |
| `download_dataset.py` | Roboflow API Key | `datasets/css-data/` | 下载 10 类数据集（yolov11 格式） |
| `inspect_dataset.py` | `datasets/css-data/data.yaml` | `dataset_preview.png`、`class_distribution.png`、质量报告 | 数据体检 |
| `prepare_data.py` | Roboflow `data.yaml` | `data_final.yaml`、5 类标签 | 过滤 5 类 + ID 重映射 + 绝对路径 |

---

## 二、方式 A：有 Roboflow API Key（推荐）

### 1. 获取 API Key
1. 登录 https://universe.roboflow.com
2. 点击右上角头像 → **Settings** → **API Keys**
3. 复制 Key（形如 `xxxxxxxxxxxxxxx`）

### 2. 下载数据集

Windows PowerShell：
```powershell
$env:ROBOFLOW_API_KEY = "你的key"
python download_dataset.py
```

Linux / macOS：
```bash
export ROBOFLOW_API_KEY="你的key"
python download_dataset.py
```

> 若数据集版本不是 v1（网页右上角可查看），用 `--version` 指定，例如 `python download_dataset.py --version 2`。

### 3. 检查数据
```powershell
python inspect_dataset.py
```
输出 `dataset_preview.png`（标注可视化）、`class_distribution.png`（类别分布）与控制台质量报告。

### 4. 精简为 5 个核心类别
```powershell
python prepare_data.py
```
生成 `data_final.yaml`（绝对路径 + 5 类），并自动备份原始 10 类标注到
`datasets/css-data/labels_backup_10cls/`。

---

## 三、方式 B：无 API Key（网页手动下载）

1. 打开数据集页面：https://universe.roboflow.com/roboflow-universe-projects/construction-site-safety
2. 点击右上角 **Download Dataset** 按钮
3. 选择格式：**YOLOv11**（或 YOLOv8，均可）→ 勾选 **"download zip to computer"** → **Continue**
4. 页面会提示需要登录 Roboflow（免费注册即可，无需付费）
5. 浏览器下载得到 `Construction-Site-Safety-1.zip`，解压后得到：
   ```
   Construction-Site-Safety-1/
   ├── README.txt
   ├── README.roboflow.txt
   ├── data.yaml
   ├── train/images/  train/labels/
   ├── valid/images/  valid/labels/
   └── test/images/   test/labels/
   ```
6. 在项目根目录创建目标结构并放入：
   ```powershell
   # 项目根目录下执行（Windows）
   New-Item -ItemType Directory -Force datasets/css-data
   # 把解压出的 train、valid、test、data.yaml 等全部移动进去
   Move-Item -Path .\Construction-Site-Safety-1\* -Destination .\datasets\css-data\
   ```
7. 校验目录结构：
   ```powershell
   python inspect_dataset.py
   ```
8. 之后同样运行：
   ```powershell
   python prepare_data.py
   ```

> 手动下载同样会得到 `data.yaml`，因此 `inspect_dataset.py` / `prepare_data.py`
> 对两条路径完全通用。

---

## 四、data.yaml 字段说明

以 Roboflow 自动生成的 `data.yaml` 为例：

```yaml
names:                  # 类别名列表，顺序即类别 ID（第 0 行=0，第 1 行=1 ...）
- Hardhat
- Mask
- NO-Hardhat
- NO-Mask
- NO-Safety Vest
- Person
- Safety Cone
- Safety Vest
- machinery
- vehicle

nc: 10                  # 类别总数 = len(names)

roboflow:               # 元信息，仅记录来源，训练时忽略
  workspace: roboflow-universe-projects
  project: construction-site-safety
  version: 1
  license: CC BY 4.0
  url: https://universe.roboflow.com/roboflow-universe-projects/construction-site-safety

test: test/images       # 测试集图片目录（相对 path）
train: train/images     # 训练集图片目录（相对 path）
val: valid/images       # 验证集图片目录（注意 Roboflow 用 valid 而非 val）
```

### 路径解析规则（Ultralytics）
- `path` 为数据集根目录；`train` / `val` / `test` 是相对 `path` 的图片目录。
- Ultralytics 会自动在图片目录同级的 `labels/` 找标注文件，
  即 `train/images/xxx.jpg` 对应 `train/labels/xxx.txt`。
- `prepare_data.py` 生成的 `data_final.yaml` 中 `path` 是**绝对路径**，
  换机器后只需把 `path` 改为新机器的绝对路径即可。

### 标注文件格式（YOLO txt）
每个 `.txt` 与同名图片一一对应，每行一个目标：

```
<类别ID> <x_center> <y_center> <width> <height>
```

- 坐标均为**归一化值**（0~1，除以图片宽/高）
- 类别 ID 必须 < `nc`

示例（640×480 图片中一个目标）：
```
0 0.531250 0.401042 0.359375 0.562500
```

---

## 五、5 个核心类别说明

| 新 ID | 类别 | 含义 | 检测目标 |
| --- | --- | --- | --- |
| 0 | Hardhat | 戴安全帽 | 合规（√） |
| 1 | NO-Hardhat | 未戴安全帽 | 违规（×） |
| 2 | Safety Vest | 穿反光衣 | 合规（√） |
| 3 | NO-Safety Vest | 未穿反光衣 | 违规（×） |
| 4 | Person | 人 | 辅助目标 |

被忽略的 5 类：`Mask`、`NO-Mask`（口罩）、`Safety Cone`（锥桶）、`machinery`（机械）、`vehicle`（车辆）。

> 提示：训练目标其实可以只保留 4 类（去掉 Person），因为违规检测只关心
> Hardhat / NO-Hardhat / Safety Vest / NO-Safety Vest。保留 Person 可辅助模型
> 学习"人"这一上下文，是否保留可自由调整（编辑 `prepare_data.py` 顶部
> `KEEP_CLASSES` 字典即可）。

---

## 六、常见问题

| 问题 | 解决 |
| --- | --- |
| `未设置 ROBOFLOW_API_KEY` | 见上文「获取 API Key」，二选一传入 |
| 版本 v1 不存在 / 下载失败 | `--version` 指定正确版本；检查网络 |
| `datasets/css-data/` 已存在导致跳过 | 删除后重下：`Remove-Item -Recurse -Force datasets\css-data` |
| `cv2.imread` 报错 / 图片损坏 | 删除损坏图片及其同名 label，重新 `inspect` |
| 柱状图/预览图中文显示为方块 | 图表已使用英文标签，不受影响 |
| 想要更多类别 | 编辑 `prepare_data.py` 的 `KEEP_CLASSES` 并重跑（原始标注已备份） |
