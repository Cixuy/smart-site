"""
Pydantic 请求 / 响应模型。

包含：
    CameraStartRequest   动态启动摄像头请求体（与 configs/*.yaml 字段对齐）
    CameraStatusOut      摄像头运行状态
    AlertSummaryOut      告警回放摘要
    ServiceHealthOut     AI 服务自身健康状态
"""

from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, Field


class CameraStartRequest(BaseModel):
    """POST /api/cameras/start 请求体。"""

    camera_id: str = Field(..., description="摄像头唯一 ID，如 CAM_01")
    camera_name: str = Field(default="", description="摄像头名称")
    rtsp_url: str = Field(..., description="视频流地址（RTSP/RTMP/本地文件路径）")
    location_name: str = Field(default="", description="点位/区域名称")

    # 模型与检测参数（缺省走全局配置）
    model_path: Optional[str] = Field(default=None, description="模型路径，缺省用全局配置")
    detect_interval: int = Field(default=3, ge=1, description="跳帧检测间隔（每 N 帧检测一次）")
    conf_threshold: float = Field(default=0.25, ge=0.0, le=1.0, description="置信度阈值")
    iou_threshold: float = Field(default=0.45, ge=0.0, le=1.0, description="NMS IoU 阈值")
    cooldown_seconds: int = Field(default=15, ge=0, description="同类违规防抖冷却秒数")

    # 截图与推送
    screenshot_dir: Optional[str] = Field(default=None, description="截图目录，缺省用全局截图根目录")
    screenshot_base_url: str = Field(default="", description="截图 HTTP 基础 URL")
    java_endpoint: str = Field(default="", description="Java 告警接收地址")

    # 后端对接（真实 Spring Boot AlarmCreateDTO）
    project_id: Optional[int] = Field(default=None, description="后端项目主键 projectId（必填）")
    backend_camera_id: Optional[int] = Field(default=None, description="后端摄像头主键 cameraId（Long）")
    device_id: Optional[int] = Field(default=None, description="后端设备主键 deviceId")
    monitor_point_id: Optional[int] = Field(default=None, description="后端监测点位主键 monitorPointId")
    threshold_range: str = Field(default="", description="阈值范围描述（如 conf>=0.25）")
    backend_auth_enabled: bool = Field(default=False, description="后端鉴权开关（true 时自动登录）")
    backend_username: str = Field(default="", description="后端登录用户名（鉴权开启时）")
    backend_password: str = Field(default="", description="后端登录密码（鉴权开启时）")
    login_endpoint: str = Field(default="", description="后端登录接口，缺省由 java_endpoint 推导")
    screenshot_endpoint: str = Field(
        default="",
        description="截图关联接口模板（含 {alarm_id} 占位符，如 "
        "http://host:8080/api/ai/alarms/{alarm_id}/screenshot），为空时不关联",
    )

    # 过滤
    enabled_classes: list[str] = Field(default_factory=list, description="启用的违规类别名列表")
    roi_polygon: Optional[list[list[float]]] = Field(
        default=None, description="ROI 多边形顶点 [[x1,y1],[x2,y2],...]"
    )

    # 重连
    max_reconnect: Optional[int] = Field(default=None, description="最大重连次数，缺省用全局配置")
    reconnect_delay: Optional[float] = Field(default=None, description="重连间隔秒，缺省用全局配置")


class CameraStatusOut(BaseModel):
    """摄像头运行状态（GET /api/cameras/status 元素）。"""

    camera_id: str
    camera_name: str = ""
    location_name: str = ""
    state: str = Field(..., description="running/connecting/error/stopped")
    fps: float = 0.0
    alert_count: int = 0
    last_alert_time: Optional[str] = None
    last_error: Optional[str] = None


class AlertSummaryOut(BaseModel):
    """告警回放摘要（GET /api/cameras/{camera_id}/replay 元素）。"""

    event_id: str
    violation_type: str
    confidence: float
    severity: str
    timestamp: str
    screenshot_url: str


class ServiceHealthOut(BaseModel):
    """AI 服务自身健康状态（GET /api/health）。"""

    service: str = "ai-safety-service"
    status: str = Field(..., description="ok / degraded")
    version: str = "1.0.0"
    gpu_available: bool = False
    gpu_name: Optional[str] = None
    model_loaded: bool = False
    model_path: Optional[str] = None
    running_cameras: int = 0
    camera_ids: list[str] = Field(default_factory=list)
