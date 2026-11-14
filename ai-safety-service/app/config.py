"""
全局配置模块。

集中管理服务级配置：模型路径、推理设备、截图目录、推送缓冲目录、网络超时等。
所有配置优先从环境变量读取（便于容器化/部署），未设置时使用内置默认值。
摄像头级别的配置（每路一路 YAML）由 camera_manager / monitor 自行解析，不在此处。

环境变量速查（均带 AI_ 前缀）：
    AI_MODEL_PATH        模型 .pt 文件路径（默认 models/construction_safety_v1.pt）
    AI_DEVICE            推理设备 auto/cuda/cpu（默认 auto）
    AI_HALF              是否启用半精度推理 true/false（默认 true）
    AI_CONFIGS_DIR       摄像头配置目录（默认 configs）
    AI_SCREENSHOT_ROOT   截图根目录（默认 ../ai_image，即 build-sec 根目录下的 ai_image，
                         后端通过 /api/ai_image/** 静态访问）
    AI_PENDING_DIR       推送失败缓冲目录（默认 pending_alerts）
    AI_HOST              服务监听地址（默认 0.0.0.0）
    AI_PORT              服务监听端口（默认 9000）
    AI_RETRY_INTERVAL    堆积告警重发间隔秒（默认 30）
    AI_MAX_RECONNECT     视频流最大重连次数（默认 10）
    AI_RECONNECT_DELAY   重连间隔秒（默认 3）
    AI_REPLAY_SIZE       每路摄像头告警回放条数（默认 10）
    AI_HEALTH_TIMEOUT    Java 健康检查超时秒（默认 2）
    AI_HTTP_TIMEOUT      告警推送超时秒（默认 5）

后端对接（摄像头 YAML 未配置时兜底）：
    AI_BACKEND_PROJECT_ID        后端项目主键 projectId（必填）
    AI_BACKEND_CAMERA_ID         后端摄像头主键 cameraId（Long）
    AI_BACKEND_DEVICE_ID         后端设备主键 deviceId
    AI_BACKEND_MONITOR_POINT_ID  后端监测点位主键 monitorPointId
    AI_BACKEND_AUTH_ENABLED      后端鉴权开关（true=开启，默认 false）
    AI_BACKEND_USERNAME          后端登录用户名（鉴权开启时）
    AI_BACKEND_PASSWORD          后端登录密码（鉴权开启时）
    AI_BACKEND_SCREENSHOT_ENDPOINT 截图关联接口模板（含 {alarm_id} 占位符）

后端摄像头同步（backend_sync.py，自动从后端拉取摄像头列表启动监测）：
    AI_BACKEND_SYNC_ENABLED        同步开关（默认 false）
    AI_BACKEND_SYNC_BASE_URL       后端 base URL（含 context-path），
                                   如 http://127.0.0.1:8080/api
    AI_BACKEND_SYNC_INTERVAL       同步周期秒（默认 60）
    AI_BACKEND_SYNC_CONF           默认置信度阈值（默认 0.25）
    AI_BACKEND_SYNC_IOU            默认 IoU（默认 0.45）
    AI_BACKEND_SYNC_DETECT_INTERVAL 默认检测间隔秒（默认 3）
    AI_BACKEND_SYNC_COOLDOWN       默认告警冷却秒（默认 15）
    AI_BACKEND_SYNC_CLASSES        默认启用类别（逗号分隔，空=全部）
    AI_BACKEND_SYNC_MODEL_PATH     可选：覆盖全局模型路径
    AI_BACKEND_SYNC_SCREENSHOT_HOST 截图外链使用的 AI 服务地址（默认 127.0.0.1）
    AI_BACKEND_SYNC_LOGIN_ENDPOINT 登录接口覆盖（默认 {base}/auth/login）
    AI_BACKEND_SYNC_TIMEOUT        后端请求超时秒（默认 5）
"""

from __future__ import annotations

import os
from pathlib import Path


def _env_bool(name: str, default: bool) -> bool:
    """解析环境变量布尔值：true/1/yes 视为 True，其余按默认值处理。"""
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in ("1", "true", "yes", "on")


def _env_int(name: str, default: int | None = None) -> int | None:
    """解析环境变量整数；缺失或非法时返回默认值。"""
    raw = os.getenv(name)
    if raw is None or not raw.strip():
        return default
    try:
        return int(raw)
    except ValueError:
        return default


class Settings:
    """服务级全局配置单例。"""

    def __init__(self) -> None:
        # 项目根目录：app/config.py -> 上级目录
        self.project_root: Path = Path(__file__).resolve().parent.parent

        # 模型与推理
        model_path = os.getenv("AI_MODEL_PATH", "models/construction_safety_v1.pt")
        self.model_path: Path = self._resolve(model_path)
        self.device: str = os.getenv("AI_DEVICE", "auto").strip().lower()
        self.half_precision: bool = _env_bool("AI_HALF", True)

        # 目录
        self.configs_dir: Path = self._resolve(os.getenv("AI_CONFIGS_DIR", "configs"))
        # 截图默认存到 build-sec 根目录的 ai_image 下（与后端 /api/ai_image/** 静态映射一致），
        # 即 project_root（ai-safety-service）的上级目录 build-sec/ai_image；
        # 摄像头 YAML 不配 screenshot_dir 时自动回落到 build-sec/ai_image/{camera_id}/
        _default_screenshot_root = self.project_root.parent / "ai_image"
        self.screenshot_root: Path = self._resolve(
            os.getenv("AI_SCREENSHOT_ROOT", str(_default_screenshot_root))
        ).resolve()
        self.pending_alert_dir: Path = self._resolve(os.getenv("AI_PENDING_DIR", "pending_alerts"))

        # 网络与重连
        self.host: str = os.getenv("AI_HOST", "0.0.0.0")
        self.port: int = int(os.getenv("AI_PORT", "9000"))
        self.retry_interval: float = float(os.getenv("AI_RETRY_INTERVAL", "30"))
        self.max_reconnect: int = int(os.getenv("AI_MAX_RECONNECT", "10"))
        self.reconnect_delay: float = float(os.getenv("AI_RECONNECT_DELAY", "3"))
        self.health_check_timeout: float = float(os.getenv("AI_HEALTH_TIMEOUT", "2"))
        self.http_timeout: float = float(os.getenv("AI_HTTP_TIMEOUT", "5"))

        # 后端对接（真实 Spring Boot；摄像头 YAML 未配置时兜底）
        self.backend_project_id: int | None = _env_int("AI_BACKEND_PROJECT_ID")
        self.backend_camera_id: int | None = _env_int("AI_BACKEND_CAMERA_ID")
        self.backend_device_id: int | None = _env_int("AI_BACKEND_DEVICE_ID")
        self.backend_monitor_point_id: int | None = _env_int("AI_BACKEND_MONITOR_POINT_ID")
        self.backend_auth_enabled: bool = _env_bool("AI_BACKEND_AUTH_ENABLED", False)
        self.backend_username: str = os.getenv("AI_BACKEND_USERNAME", "")
        self.backend_password: str = os.getenv("AI_BACKEND_PASSWORD", "")
        self.backend_screenshot_endpoint: str = os.getenv("AI_BACKEND_SCREENSHOT_ENDPOINT", "")

        # ---- 后端摄像头同步（backend_sync.py）----
        self.backend_sync_enabled: bool = _env_bool("AI_BACKEND_SYNC_ENABLED", False)
        self.backend_sync_base_url: str = os.getenv("AI_BACKEND_SYNC_BASE_URL", "").strip().rstrip("/")
        self.backend_sync_interval: float = float(os.getenv("AI_BACKEND_SYNC_INTERVAL", "60"))
        self.backend_sync_conf: float = float(os.getenv("AI_BACKEND_SYNC_CONF", "0.25"))
        self.backend_sync_iou: float = float(os.getenv("AI_BACKEND_SYNC_IOU", "0.45"))
        self.backend_sync_detect_interval: int = int(os.getenv("AI_BACKEND_SYNC_DETECT_INTERVAL", "3"))
        self.backend_sync_cooldown: float = float(os.getenv("AI_BACKEND_SYNC_COOLDOWN", "15"))
        self.backend_sync_classes: list[str] = [
            c.strip() for c in os.getenv("AI_BACKEND_SYNC_CLASSES", "").split(",") if c.strip()
        ]
        self.backend_sync_model_path: str = os.getenv("AI_BACKEND_SYNC_MODEL_PATH", "").strip()
        self.backend_sync_screenshot_host: str = os.getenv(
            "AI_BACKEND_SYNC_SCREENSHOT_HOST", "127.0.0.1"
        ).strip()
        self.backend_sync_login_endpoint: str = os.getenv("AI_BACKEND_SYNC_LOGIN_ENDPOINT", "").strip()
        self.backend_sync_timeout: float = float(os.getenv("AI_BACKEND_SYNC_TIMEOUT", "5"))

        # 其他
        self.replay_size: int = int(os.getenv("AI_REPLAY_SIZE", "10"))

        self.ensure_dirs()

    def _resolve(self, raw: str) -> Path:
        """将相对路径解析为基于项目根目录的绝对路径。"""
        p = Path(raw).expanduser()
        return p if p.is_absolute() else self.project_root / p

    def ensure_dirs(self) -> None:
        """确保截图目录与推送缓冲目录存在。"""
        for d in (self.screenshot_root, self.pending_alert_dir):
            d.mkdir(parents=True, exist_ok=True)

    def __repr__(self) -> str:  # pragma: no cover - 仅调试用
        return (
            f"Settings(model={self.model_path}, device={self.device}, "
            f"half={self.half_precision}, configs={self.configs_dir})"
        )


# 模块级单例：全项目共享
settings = Settings()
