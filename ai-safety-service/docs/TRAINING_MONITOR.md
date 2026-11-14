# 训练监控说明

本文档解释如何阅读 YOLO11 训练日志与产物，以及如何判断下一步动作。

## 1. 训练产物位置

训练完成后（默认输出到 `runs/construction/<阶段名>/`）：

| 文件 | 说明 |
| --- | --- |
| `results.png` | 训练曲线总览（loss 与 mAP 随 epoch 的变化） |
| `confusion_matrix.png` | 验证集混淆矩阵（类别间误检情况） |
| `val_batch0_pred.jpg` 等 | 验证集批次的预测可视化（框是否正确） |
| `train_batch*.jpg` | 训练增强后的样本（检查增强是否合理） |
| `weights/best.pt` | 验证集 mAP 最高的权重（**部署用这个**） |
| `weights/last.pt` | 最后一个 epoch 的权重（二阶段热启动用这个） |
| `args.yaml` | 本次训练的全部超参，方便复现 |

## 2. 关键图表怎么看

### results.png（最重要）
曲线分两列：
- **左列 loss**：`train/box_loss`、`train/cls_loss`、`train/dfl_loss`（训练损失，应稳步下降）与 `val/box_loss`、`val/cls_loss`、`val/dfl_loss`（验证损失）。
  - 验证 loss 持续下降 → 正常；
  - 训练 loss 降而验证 loss 掉头上升 → **过拟合**（见第 4 节）。
- **右列指标**：`metrics/precision`、`metrics/recall`、`metrics/mAP50`、`metrics/mAP50-95`。
  - `mAP50` 更关注"框打没打中"，`mAP50-95` 更严格（框要贴合）。
  - 工地小目标场景，观察曲线是否在中后期仍上涨——若 70 epoch 后还在涨，说明还没收敛，可加 epoch。

### confusion_matrix.png
- 对角线越亮越好（正确分类多）。
- 行 = 真实类别，列 = 预测类别。关注两处：
  - `Hardhat` 与 `NO-Hardhat`、`Safety Vest` 与 `NO-Safety Vest` 之间互串 → 类别特征相似，需更多难例样本或更高分辨率。
  - `Person` 与各类别互串 → 检测框偏大/偏小，需调 anchor 相关参数或加数据。

### val_batch*_pred.jpg
- 绿框 = 正确预测，看**漏检**（该有框没框）与**误检**（无目标却有框）是否集中在：
  - 远处小目标（人头 20~40px）→ 分辨率/小目标问题；
  - 遮挡目标 → 增强（copy_paste）与数据多样性问题；
  - 逆光/暗部 → HSV 增强是否覆盖。

## 3. mAP 及格线建议（工地场景）

| 指标 | 判定 | 建议动作 |
| --- | --- | --- |
| mAP@0.5 ≥ 0.7 | 可进联调 | 部署试跑，收集真实误检 |
| mAP@0.5 ≥ 0.8 | 良好 | 可考虑量化/TensorRT 压缩 |
| mAP@0.5 < 0.6 | 未达标 | 见第 4 节，优先补数据 |
| mAP@0.5:0.95 明显低于 mAP@0.5 × 0.6 | 框不够贴合 | 提高分辨率或调回归损失（box 权重） |

> 注：mAP@0.5 是"检测有没有"，联调报警系统用它；mAP@0.5:0.95 是"框准不准"，影响后续跟踪与测距。

## 4. 何时加数据 vs 何时调超参

### 优先加数据（数据问题）
- **小目标漏检**：收集/增强远处人物样本（工地监控裁剪放大），或在数据集中补充低分辨率人脸/人体。
- **类别不平衡**：`Person` 标注多、`NO-Hardhat` 少时，对少类做复制增强或补采集。
- **误检高**（precision 低）：加"无目标但易误报"的负样本（纯背景、工具车、杂物）。
- **特定场景失败**（逆光/夜视/粉尘）：按场景维度补充数据。

### 优先调超参（训练/模型问题）
- **欠拟合**（loss 不降，mAP 低且稳定）：
  - 加大 `epochs`、提高 `lr0`（回 0.01 量级）、换 SGD/AdamW；
  - 关掉过强增强（`mosaic=0.5`、`hsv_*` 降低），让模型先学得动。
- **过拟合**（val loss 掉头上升）：
  - 增强正则：`mosaic`/`copy_paste`/`mixup` 提高，`dropout` 开启，`weight_decay` 加大；
  - 训练集随机截取 + 早停 patience 调低。
- **框不贴合**（mAP@0.5:0.95 差）：
  - 提高 `imgsz`（640→960 已在二阶段做）；
  - 延长 `close_mosaic` 轮数，让后期框回归更稳定。
- **GPU 显存 OOM**：降 `batch`、降 `imgsz`、保持 `amp=True`。

### 决策顺序建议
1. 先跑通 Stage1（640）看 loss 是否收敛、有无路径/数据错误；
2. Stage2（960）主要解决小目标召回，看 mAP@0.5 提升是否 > 0.02；
3. 若 mAP@0.5 ≥ 0.7 且误检可接受 → 进联调；否则先补数据、再考虑调参，**一次只改一个变量**并记录对比。

## 5. 快速检查命令

```bash
# 用 best.pt 在测试集上评估（同训练指标口径）
python -c "from ultralytics import YOLO; m=YOLO('runs/construction/yolo11s_stage2_960/weights/best.pt').val(data='datasets/css-data/data_final.yaml', imgsz=960); print(m.box.map50, m.box.map)"

# 导出部署格式
python scripts/export_model.py
```
