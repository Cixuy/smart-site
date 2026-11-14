#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_env.py — YOLO11 训练环境健康检查脚本

功能:
    1. 检查系统 / Python 基本信息
    2. 检查 CUDA 是否可用、GPU 型号与显存、nvidia-smi 驱动信息
    3. 检查关键依赖包版本 (torch / torchvision / ultralytics / opencv-python /
       pyyaml / numpy / matplotlib / roboflow)
    4. 加载 yolo11s.pt 并对随机 Tensor 执行 GPU 推理，验证推理链路
    5. 输出带 ✅/❌ 标记的环境健康报告；存在关键项失败时以非 0 退出码结束

用法:
    python verify_env.py
"""

import platform
import shutil
import subprocess
import sys
import time

# Windows 控制台默认 GBK 编码，强制 stdout/stderr 使用 UTF-8，避免 ✅/❌ 等
# 字符触发 UnicodeEncodeError；Linux 下 reconfigure 亦无害。
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

# ---------------------------------------------------------------------------
# 显示工具
# ---------------------------------------------------------------------------
def ok(msg):    print(f"  \u2705 {msg}")
def bad(msg):   print(f"  \u274c {msg}")
def warn(msg):  print(f"  \u26a0\ufe0f  {msg}")
def h2(title):  print(f"\n--- {title} ---")

FAILURES: list = []
WARNINGS: list = []


def check(name: str, fn, critical: bool = True) -> bool:
    """执行检查项 fn()：返回 False 或抛出异常视为失败。

    Args:
        name: 检查项名称
        fn:   无参可调用对象
        critical: 是否为关键项（关键项失败计入 FAILURES，非关键项只计入 WARNINGS）
    Returns:
        检查是否通过
    """
    try:
        result = fn()
        if result is False:
            raise AssertionError("检查未通过")
        ok(f"[通过] {name}")
        return True
    except Exception as exc:  # noqa: BLE001 —— 检查脚本需要捕获所有异常
        if critical:
            FAILURES.append(f"{name} -> {exc}")
            bad(f"[失败] {name} -> {exc}")
        else:
            WARNINGS.append(f"{name} -> {exc}")
            warn(f"[警告] {name} -> {exc}")
        return False


def fmt_gb(nbytes: int) -> str:
    return f"{nbytes / (1024 ** 3):.2f} GB"


def version_at_least(ver: str, minimum: str) -> bool:
    """比较 'a.b.c' 形式的版本号是否 >= minimum（忽略 +cu121 等后缀）。"""
    def _parts(s):
        core = s.split("+")[0]
        return [int(x) for x in core.split(".")[:3]]
    return _parts(ver) >= _parts(minimum)


# ---------------------------------------------------------------------------
# 各检查项
# ---------------------------------------------------------------------------
def check_system():
    h2("1. 系统信息")
    print(f"  操作系统 : {platform.platform()}")
    print(f"  Python   : {sys.version.split()[0]} ({platform.python_implementation()})")
    print(f"  解释器   : {sys.executable}")
    if sys.version_info < (3, 12):
        warn("Python 版本低于 3.12，部分依赖可能缺少对应轮子，建议重建环境。")


def check_cuda() -> bool:
    h2("2. CUDA 与 GPU")

    def _cuda_available():
        import torch
        assert torch.cuda.is_available(), (
            "torch.cuda.is_available() 返回 False。请检查 NVIDIA 驱动 / torch 是否为 cu121 版本，"
            "详见 README.md【五、常见问题排查 - 5.1】"
        )
        print(f"      torch 构建的 CUDA 版本 : {torch.version.cuda}")
        return True

    cuda_ok = check("CUDA 可用（torch.cuda.is_available）", _cuda_available)

    def _nvidia_smi():
        smi = shutil.which("nvidia-smi")
        assert smi, "未找到 nvidia-smi，请确认 NVIDIA 驱动已安装"
        out = subprocess.run(
            [smi, "--query-gpu=name,driver_version,memory.total,memory.free",
             "--format=csv,noheader,nounits"],
            capture_output=True, text=True,
        )
        assert out.returncode == 0, f"nvidia-smi 执行失败: {out.stderr.strip()}"
        for line in out.stdout.strip().splitlines():
            print(f"      nvidia-smi -> {line}")
        return True

    check("nvidia-smi 驱动可见", _nvidia_smi)

    def _gpu_info():
        import torch
        assert torch.cuda.is_available(), "CUDA 不可用，无法读取 GPU 信息"
        count = torch.cuda.device_count()
        print(f"      PyTorch 可见 GPU 数量 : {count}")
        for i in range(count):
            props = torch.cuda.get_device_properties(i)
            print(f"      GPU[{i}] {torch.cuda.get_device_name(i)}")
            print(f"            显存总量 : {fmt_gb(props.total_memory)}")
            print(f"            算力能力 : {props.major}.{props.minor}")
        return True

    check("GPU 型号与显存（PyTorch）", _gpu_info)
    return cuda_ok


def check_dependencies():
    h2("3. 关键依赖版本")

    def _torch():
        import torch
        print(f"      torch       : {torch.__version__}")
        try:
            cudnn = torch.backends.cudnn.version()
            print(f"      cuDNN       : {cudnn}")
        except Exception:
            pass
        return True

    check("torch", _torch)

    def _torchvision():
        import torchvision
        print(f"      torchvision : {torchvision.__version__}")
        return True

    check("torchvision", _torchvision)

    def _ultralytics():
        import ultralytics
        v = ultralytics.__version__
        print(f"      ultralytics : {v}")
        assert version_at_least(v, "8.3.0"), "版本过低，请升级: pip install -U ultralytics"
        return True

    check("ultralytics (>=8.3)", _ultralytics)

    def _cv2():
        import cv2
        v = cv2.__version__
        print(f"      opencv-python : {v}")
        assert version_at_least(v, "4.9"), "版本过低，请升级: pip install -U opencv-python"
        return True

    check("opencv-python (>=4.9)", _cv2)

    def _yaml():
        import yaml
        print(f"      pyyaml      : {yaml.__version__}")
        return True

    check("pyyaml (>=6.0)", _yaml)

    def _numpy():
        import numpy
        print(f"      numpy       : {numpy.__version__}")
        return True

    check("numpy", _numpy)

    def _matplotlib():
        import matplotlib
        print(f"      matplotlib  : {matplotlib.__version__}")
        return True

    check("matplotlib", _matplotlib)

    def _roboflow():
        import roboflow
        v = getattr(roboflow, "__version__", "unknown")
        print(f"      roboflow    : {v}")
        return True

    check("roboflow（可选，仅下载数据集时需要）", _roboflow, critical=False)


def check_inference(cuda_ok: bool):
    h2("4. YOLO11s 推理链路验证")
    try:
        import torch
        from ultralytics import YOLO
    except Exception as exc:  # noqa: BLE001
        bad(f"无法导入 torch / ultralytics，跳过推理验证: {exc}")
        FAILURES.append(f"推理链路 -> {exc}")
        return

    device = "cuda:0" if cuda_ok else "cpu"
    model_path = "yolo11s.pt"
    print(f"      加载模型 {model_path}（首次运行会自动从 GitHub 下载，约 19MB）...")

    try:
        model = YOLO(model_path)
        dummy = torch.randn(1, 3, 640, 640)
        if cuda_ok:
            dummy = dummy.to("cuda")

        # 预热：触发 CUDA kernel 编译 / 模型首次加载
        for _ in range(2):
            model.predict(dummy, verbose=False)
        if cuda_ok:
            torch.cuda.synchronize()

        # 计时（batch=1, imgsz=640）
        rounds = 5
        t0 = time.time()
        for _ in range(rounds):
            model.predict(dummy, verbose=False)
        if cuda_ok:
            torch.cuda.synchronize()
        avg_ms = (time.time() - t0) / rounds * 1000

        print(f"      推理耗时 : {avg_ms:.1f} ms/张（batch=1, imgsz=640, device={device}）")
        ok("YOLO11s 推理链路正常")
    except Exception as exc:  # noqa: BLE001
        bad(f"YOLO11s 推理失败: {exc}")
        FAILURES.append(f"推理链路 -> {exc}")


def summary():
    print("\n--- 5. 环境健康报告汇总 ---")
    print()
    if FAILURES:
        print(f"  \u274c 共 {len(FAILURES)} 项关键检查失败，请修复后重新运行本脚本：")
        for i, f in enumerate(FAILURES, 1):
            print(f"     {i}. {f}")
        if WARNINGS:
            print(f"     另有 {len(WARNINGS)} 项非关键警告（不影响训练）：")
            for w in WARNINGS:
                print(f"     - {w}")
    elif WARNINGS:
        print(f"  \u2705 关键检查全部通过，另有 {len(WARNINGS)} 项非关键警告：")
        for w in WARNINGS:
            print(f"     - {w}")
        print("\n  \u2705 环境已就绪，可开始训练！")
    else:
        print("  \u2705 全部检查通过，环境已就绪，可开始训练！")
    print("  " + "=" * 55)


def main():
    print()
    print("  YOLO11 训练环境健康报告")
    print(f"  生成时间 : {time.strftime('%Y-%m-%d %H:%M:%S')}")

    check_system()
    cuda_ok = check_cuda()
    check_dependencies()
    check_inference(cuda_ok)
    summary()

    return 1 if FAILURES else 0


if __name__ == "__main__":
    sys.exit(main())
