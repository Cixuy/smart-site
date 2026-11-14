"""
FastAPI 入口。

提供：
    - lifespan：启动时自动加载 configs/*.yaml 启动全部摄像头，关闭时全部停止；
    - 管理 API：摄像头启停 / 状态 / 回放 / 服务健康；
    - 截图静态文件服务：/screenshots -> 全局截图根目录。

启动方式：
    python -m app.main
    或
    uvicorn app.main:app --host 0.0.0.0 --port 9000 --workers 1
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.staticfiles import StaticFiles

from app.backend_sync import BackendCameraSyncer
from app.camera_manager import CameraManager
from app.config import settings
from app.detector import YOLODetector
from app.models.schemas import (
    AlertSummaryOut,
    CameraStartRequest,
    CameraStatusOut,
    ServiceHealthOut,
)

# ------------------------------------------------------------------ #
# 日志
# ------------------------------------------------------------------ #
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("ai_monitor")

# 降低第三方库噪声
for noisy in ("ultralytics", "urllib3", "uvicorn.access"):
    logging.getLogger(noisy).setLevel(logging.WARNING)


# ------------------------------------------------------------------ #
# 生命周期
# ------------------------------------------------------------------ #
@asynccontextmanager
async def lifespan(app: FastAPI):
    """服务启动/关闭钩子：加载 YAML 摄像头；可开启后端摄像头同步；关闭时全部停止。"""
    settings.ensure_dirs()
    manager = CameraManager.get_instance()
    started = manager.start_from_configs(settings.configs_dir)
    logger.info("AI 监测服务启动完成，自动加载摄像头 %d 路", started)

    # 后端摄像头同步（AI_BACKEND_SYNC_ENABLED=true 时启用）
    global syncer
    if settings.backend_sync_enabled:
        if not settings.backend_sync_base_url:
            logger.error(
                "backend_sync_enabled=true 但未配置 AI_BACKEND_SYNC_BASE_URL，"
                "后端摄像头同步器未启动"
            )
        else:
            syncer = BackendCameraSyncer(manager)
            syncer.start()

    yield

    if syncer is not None:
        syncer.stop()
    manager.stop_all()
    logger.info("AI 监测服务已关闭")


# 后端摄像头同步器实例（lifespan 中创建）
syncer: BackendCameraSyncer | None = None

app = FastAPI(
    title="建筑工地安全 AI 监测服务",
    description=(
        "从 RTSP 视频流实时检测工地安全违规（未戴安全帽/未穿反光衣/吸烟/明火等），"
        "命中后自动截图并 HTTP 推送告警到 Java 后端。"
    ),
    version="1.0.0",
    lifespan=lifespan,
)

manager = CameraManager.get_instance()


# ------------------------------------------------------------------ #
# 管理 API
# ------------------------------------------------------------------ #
@app.post(
    "/api/cameras/start",
    response_model=CameraStatusOut,
    summary="动态启动一路摄像头监测",
)
def start_camera(req: CameraStartRequest) -> CameraStatusOut:
    """接收摄像头 JSON 配置并启动对应监测线程。"""
    try:
        status = manager.start_camera(req.model_dump(exclude_none=True))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    return CameraStatusOut(**status)


@app.post(
    "/api/cameras/stop/{camera_id}",
    response_model=CameraStatusOut,
    summary="停止指定摄像头",
)
def stop_camera(camera_id: str) -> CameraStatusOut:
    """停止并移除指定摄像头监测线程。"""
    monitor = manager.get_monitor(camera_id)
    if monitor is None:
        raise HTTPException(status_code=404, detail=f"摄像头不存在: {camera_id}")
    if not manager.stop_camera(camera_id):
        raise HTTPException(status_code=500, detail="停止摄像头失败")
    return CameraStatusOut(**monitor.get_status())


@app.get(
    "/api/cameras/status",
    response_model=list[CameraStatusOut],
    summary="查询所有摄像头运行状态",
)
def cameras_status() -> list[CameraStatusOut]:
    """返回所有摄像头的运行状态、FPS、告警计数、最后告警时间。"""
    return [CameraStatusOut(**s) for s in manager.get_status()]


@app.get(
    "/api/cameras/{camera_id}/replay",
    response_model=list[AlertSummaryOut],
    summary="查询摄像头最近告警回放",
)
def camera_replay(camera_id: str) -> list[AlertSummaryOut]:
    """返回该摄像头最近 10 条告警缩略信息（从旧到新）。"""
    replay = manager.get_replay(camera_id)
    if replay is None:
        raise HTTPException(status_code=404, detail=f"摄像头不存在: {camera_id}")
    return [AlertSummaryOut(**item) for item in replay]


@app.get(
    "/api/health",
    response_model=ServiceHealthOut,
    summary="AI 服务自身健康检查",
)
def service_health() -> ServiceHealthOut:
    """返回 GPU 状态、模型加载状态、运行摄像头数。"""
    gpu = {}
    model_loaded = YOLODetector.is_loaded()
    if model_loaded:
        gpu = YOLODetector.get_instance().gpu_info()
    statuses = manager.get_status()
    return ServiceHealthOut(
        status="ok",
        gpu_available=bool(gpu.get("gpu_available", False)),
        gpu_name=gpu.get("gpu_name"),
        model_loaded=model_loaded,
        model_path=str(settings.model_path) if model_loaded else None,
        running_cameras=len(statuses),
        camera_ids=[s["camera_id"] for s in statuses],
    )


# ------------------------------------------------------------------ #
# 截图静态文件服务
# 目录布局: {AI_SCREENSHOT_ROOT}/{camera_id}/*.jpg
#           （默认 AI_SCREENSHOT_ROOT = build-sec 根目录下 ai_image/）
# 访问:     http://IP:9000/screenshots/{camera_id}/{filename}
# 注意: 需在所有路由之后挂载，避免覆盖 API 路由匹配。
# ------------------------------------------------------------------ #
app.mount(
    "/screenshots",
    StaticFiles(directory=str(settings.screenshot_root)),
    name="screenshots",
)


# ------------------------------------------------------------------ #
# 入口
# ------------------------------------------------------------------ #
if __name__ == "__main__":
    import uvicorn

    logger.info("启动 uvicorn: %s:%s", settings.host, settings.port)
    uvicorn.run("app.main:app", host=settings.host, port=settings.port, workers=1)
