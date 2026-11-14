"""
截图保存工具。

负责：
    1. 保存整帧 JPG 截图，文件名 {违规类型}_{YYYYMMDD_HHMMSS}.jpg；
    2. 保存违规目标的 bbox 裁剪图到 {screenshot_dir}/crops/ 子目录；
    3. 自动创建目录、处理同秒同名冲突（追加毫秒后缀）。
"""

from __future__ import annotations

from datetime import datetime
from pathlib import Path

import cv2


def make_filename(violation_type: str, now: datetime | None = None) -> str:
    """
    生成截图文件名：{类型}_{YYYYMMDD_HHMMSS}.jpg。

    同秒内若文件已存在，追加毫秒后缀保证唯一（如 xxx_103000.123456.jpg）。
    """
    now = now or datetime.now()
    base = f"{violation_type}_{now.strftime('%Y%m%d_%H%M%S')}"
    return f"{base}.jpg"


def _unique_path(directory: Path, base_name: str) -> Path:
    """若目标文件已存在则追加毫秒/序号，返回不冲突的完整路径。"""
    path = directory / base_name
    stem = Path(base_name).stem
    suffix = Path(base_name).suffix
    n = 1
    while path.exists():
        path = directory / f"{stem}_{n:02d}{suffix}"
        n += 1
    return path


def _resolve_dir(camera_id: str, screenshot_dir: str | None, root: Path | None) -> Path:
    """
    解析截图目录：
        - 显式配置 screenshot_dir 时优先使用；
        - 否则使用 root/{camera_id}（root 为全局截图根目录，
          默认指向 build-sec 根目录下 ai_image/）。
    """
    if screenshot_dir:
        d = Path(screenshot_dir).expanduser()
        if not d.is_absolute() and root is not None:
            d = root / d
    else:
        d = Path(root or Path("screenshots")) / camera_id
    d.mkdir(parents=True, exist_ok=True)
    return d


def save_screenshot(
    frame,
    camera_id: str,
    violation_type: str,
    screenshot_dir: str | None = None,
    root: Path | None = None,
) -> tuple[Path, str]:
    """
    保存整帧截图为 JPG。

    Args:
        frame:          当前 BGR 帧。
        camera_id:      摄像头 ID（用于目录组织）。
        violation_type: 违规类型（用于文件名）。
        screenshot_dir: 可选，显式截图目录；None 时用 root/{camera_id}。
        root:           可选，全局截图根目录。

    Returns:
        (保存的完整路径, 文件名)。失败时抛异常由调用方处理。
    """
    directory = _resolve_dir(camera_id, screenshot_dir, root)
    filename = make_filename(violation_type)
    path = _unique_path(directory, filename)
    cv2.imwrite(str(path), frame)
    return path, path.name


def save_crop(
    frame,
    bbox: dict,
    camera_id: str,
    violation_type: str,
    screenshot_dir: str | None = None,
    root: Path | None = None,
) -> tuple[Path, str]:
    """
    保存违规目标 bbox 区域裁剪图到 {screenshot_dir}/crops/。

    Args:
        frame: 当前 BGR 帧。
        bbox:  {"x1":..,"y1":..,"x2":..,"y2":..} 整数坐标。
        其余参数同 save_screenshot。

    Returns:
        (保存的完整路径, 文件名)。裁剪区域越界/为空时返回 (None, "")。
    """
    x1 = max(0, int(bbox["x1"]))
    y1 = max(0, int(bbox["y1"]))
    x2 = int(bbox["x2"])
    y2 = int(bbox["y2"])
    h, w = frame.shape[:2]
    x2 = min(w, x2)
    y2 = min(h, y2)

    if x2 <= x1 or y2 <= y1:
        return None, ""

    directory = _resolve_dir(camera_id, screenshot_dir, root) / "crops"
    directory.mkdir(parents=True, exist_ok=True)

    crop = frame[y1:y2, x1:x2]
    filename = make_filename(violation_type)
    path = _unique_path(directory, filename)
    cv2.imwrite(str(path), crop)
    return path, path.name
