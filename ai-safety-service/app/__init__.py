"""
建筑工地安全 AI 监测服务包。

从 RTSP/RTMP/本地视频流实时检测安全违规行为（未戴安全帽、未穿反光衣、
吸烟、明火等），命中后自动截图，并通过 HTTP POST 推送到 Java Spring 后端。

模块结构：
    main.py            FastAPI 入口 + lifespan 生命周期管理
    config.py          全局配置（环境变量 + 默认值）
    detector.py        YOLO11 检测器单例封装
    monitor.py         单路摄像头监测线程（核心循环）
    camera_manager.py  多路摄像头统一管理（单例）
    http_publisher.py  告警 HTTP 推送 + 本地落盘缓冲 + 重试
    health_check.py    Java 后端在线探测（带缓存）
    models/schemas.py  Pydantic 请求/响应模型
    utils/             帧缓冲 / 截图 / 防抖 / ROI 过滤等工具
"""

__version__ = "1.0.0"
