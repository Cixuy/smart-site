"""
用本地 mp4 文件测试 检测 -> 截图 -> 推送 全流程（无需真实 RTSP）。

该脚本直接复用生产组件（YOLODetector / HttpPublisher / Debouncer / ROIFilter
/ screenshot 工具），与线上行为一致，便于快速验证模型与链路。

用法：
    1) 先启动模拟 Java 后端（另一个终端）：
        python tests/mock_java_server.py --port 8001

    2) 运行本脚本：
        python tests/test_with_video.py --video path/to/video.mp4 \
            --config configs/camera_mock.yaml --max-frames 300

    3) 查看输出汇总与 screenshots/CAM_MOCK/ 下的截图。

参数说明：
    --video         本地视频文件（mp4/avi 等），必填
    --config        摄像头 YAML 配置（复用其检测/推送参数）
    --java          Java 告警地址，缺省取配置中的 java_endpoint
    --max-frames    最多处理的帧数，避免跑完整视频耗时过长
    --no-push       仅检测截图，不推送（调试用）
"""

from __future__ import annotations

import argparse
import logging
import sys
from datetime import datetime
from pathlib import Path

# 将项目根目录加入 sys.path，保证以任意工作目录运行都能 import app
ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import cv2  # noqa: E402
import yaml  # noqa: E402

from app.config import settings  # noqa: E402
from app.detector import YOLODetector  # noqa: E402
from app.http_publisher import HttpPublisher  # noqa: E402
from app.utils import screenshot as shot  # noqa: E402
from app.utils.debounce import Debouncer  # noqa: E402
from app.utils.roi_filter import ROIFilter  # noqa: E402

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("test_with_video")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="本地视频全流程测试")
    parser.add_argument("--video", required=True, help="本地视频文件路径（mp4 等）")
    parser.add_argument("--config", default=str(ROOT / "configs" / "camera_01.yaml"),
                        help="摄像头 YAML 配置")
    parser.add_argument("--java", default=None, help="Java 告警地址（缺省取配置）")
    parser.add_argument("--max-frames", type=int, default=300, help="最多处理帧数")
    parser.add_argument("--no-push", action="store_true", help="仅检测截图，不推送")
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    video = Path(args.video)
    if not video.is_file():
        logger.error("视频文件不存在: %s", video)
        return 1

    # 1) 读取摄像头配置
    config = yaml.safe_load(Path(args.config).read_text(encoding="utf-8")) or {}
    camera_id = config.get("camera_id", "TEST_CAM")
    camera_name = config.get("camera_name", "测试摄像头")
    location_name = config.get("location_name", "测试点位")
    screenshot_base_url = config.get("screenshot_base_url", "")
    java_endpoint = args.java or config.get("java_endpoint", "")

    detect_interval = max(1, int(config.get("detect_interval", 3)))
    conf_threshold = float(config.get("conf_threshold", 0.25))
    iou_threshold = float(config.get("iou_threshold", 0.45))
    cooldown_seconds = float(config.get("cooldown_seconds", 15))
    debounce_mode = str(config.get("debounce_mode", "grid"))
    debounce_grid_x = int(config.get("debounce_grid_x", 50))
    debounce_grid_y = int(config.get("debounce_grid_y", 50))
    enabled_classes = config.get("enabled_classes") or []

    # 2) 初始化组件（与生产代码一致）
    detector = YOLODetector.get_instance(config.get("model_path"))
    publisher = None if args.no_push else HttpPublisher(
        java_endpoint=java_endpoint,
        camera_id=camera_id,
        camera_name=camera_name,
        location_name=location_name,
        screenshot_base_url=screenshot_base_url,
        project_id=config.get("project_id") or settings.backend_project_id,
        backend_camera_id=config.get("backend_camera_id") or settings.backend_camera_id,
        device_id=config.get("device_id") or settings.backend_device_id,
        monitor_point_id=config.get("monitor_point_id") or settings.backend_monitor_point_id,
        threshold_range=config.get("threshold_range", ""),
        backend_auth_enabled=bool(
            config.get("backend_auth_enabled", settings.backend_auth_enabled)
        ),
        backend_username=config.get("backend_username") or settings.backend_username,
        backend_password=config.get("backend_password") or settings.backend_password,
        login_endpoint=config.get("login_endpoint", ""),
        screenshot_endpoint=(
            config.get("screenshot_endpoint") or settings.backend_screenshot_endpoint
        ),
    )
    debouncer = Debouncer(
        cooldown_seconds=cooldown_seconds,
        mode=debounce_mode,
        grid_x=debounce_grid_x,
        grid_y=debounce_grid_y,
    )
    roi = ROIFilter(config.get("roi_polygon"))

    # 3) 打开视频（FFMPEG 后端 + 低缓冲）
    cap = cv2.VideoCapture(str(video), cv2.CAP_FFMPEG)
    if not cap.isOpened():
        logger.error("无法打开视频: %s", video)
        return 1
    cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)

    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    logger.info(
        "开始测试: %s (总帧数=%d, 检测间隔=%d, 阈值=%.2f, 类别=%s)",
        video.name, total_frames, detect_interval, conf_threshold,
        enabled_classes or "ALL",
    )

    # 4) 循环：读帧 -> 跳帧检测 -> ROI/防抖 -> 截图 -> 推送
    frame_idx = 0
    det_count = 0
    alert_count = 0
    push_ok = 0
    while frame_idx < args.max_frames:
        ret, frame = cap.read()
        if not ret or frame is None:
            logger.info("视频读取结束（或失败）: 已处理 %d 帧", frame_idx)
            break
        frame_idx += 1

        if frame_idx % detect_interval != 0:
            continue

        # 检测（启用类别过滤时直接传给模型提前过滤）
        try:
            detections = detector.predict(
                frame, conf=conf_threshold, iou=iou_threshold,
                enabled_classes=enabled_classes or None,
            )
        except Exception as exc:
            logger.error("推理异常: %s", exc)
            continue

        # 同帧同类型只处理一次（置信度最高者），与生产 monitor 行为一致
        seen_types: set[str] = set()
        for det in sorted(detections, key=lambda d: d.get("confidence", 0.0), reverse=True):
            bbox = det["bbox"]
            cx = (bbox["x1"] + bbox["x2"]) / 2.0
            cy = (bbox["y1"] + bbox["y2"]) / 2.0
            if not roi.contains(cx, cy):
                continue
            vtype = det["class_name"]
            if vtype in seen_types:
                continue
            if not debouncer.should_trigger(vtype, cx, cy):
                continue
            seen_types.add(vtype)

            det_count += 1
            # 截图（全图 + 裁剪）
            try:
                _, filename = shot.save_screenshot(
                    frame, camera_id, vtype, screenshot_dir=None, root=settings.screenshot_root
                )
                shot.save_crop(
                    frame, bbox, camera_id, vtype, screenshot_dir=None, root=settings.screenshot_root
                )
            except Exception as exc:
                logger.error("截图失败: %s", exc)
                filename = ""

            alert_count += 1
            ts = datetime.now().isoformat()
            event_id = f"{camera_id}_{datetime.now().strftime('%Y%m%d_%H%M%S')}_{vtype}"

            if publisher is not None:
                alert = publisher.build_alert(
                    event_id=event_id,
                    violation_type=vtype,
                    confidence=det["confidence"],
                    screenshot_filename=filename or vtype,
                    bbox=bbox,
                    timestamp=ts,
                )
                if publisher.publish_alert(alert):
                    push_ok += 1
                logger.info(
                    "[帧 %d] 违规=%s conf=%.3f 截图=%s",
                    frame_idx, vtype, det["confidence"], filename or "无",
                )
            else:
                logger.info("[帧 %d] 违规=%s conf=%.3f 截图=%s（已跳过推送）",
                            frame_idx, vtype, det["confidence"], filename or "无")

    # 5) 收尾
    cap.release()
    if publisher is not None:
        publisher.stop()

    logger.info("=" * 60)
    logger.info("测试完成汇总")
    logger.info("  处理帧数      : %d / %d", frame_idx, total_frames)
    logger.info("  命中目标数    : %d", det_count)
    logger.info("  触发告警数    : %d", alert_count)
    logger.info("  推送成功数    : %d", push_ok if publisher else 0)
    logger.info("  截图目录      : %s", settings.screenshot_root / camera_id)
    if java_endpoint:
        logger.info("  Java 端点     : %s", java_endpoint)
    logger.info("=" * 60)
    return 0


if __name__ == "__main__":
    sys.exit(main())
