"""
极简 Flask 服务，模拟真实 Spring Boot 后端的告警接收接口（Result 契约）。

用于无 Java 后端时独立测试本服务的 检测 -> 截图 -> 推送 全流程。

接口（与真实后端对齐，context-path=/api）：
    POST /api/alarms      接收 AlarmCreateDTO
                         成功: {"code":0,"msg":"success","data":{"id":N}}
                         校验失败: {"code":400,"msg":"参数校验失败: ..."}
    GET  /api/alarms      探测/统计（健康探测打到该地址返回 200）
    GET  /api/video/cameras 摄像头分页列表（VideoCameraVO 子集，供同步器拉取）
    POST /api/auth/login  模拟登录（username/password 非空即成功）
                         成功: {"code":0,"msg":"success","data":{"token":"mock-token-..."}}

用法：
    python tests/mock_java_server.py --port 8001

依赖：flask（见 requirements.txt 测试依赖段）
"""

from __future__ import annotations

import argparse
import time
from datetime import datetime

from flask import Flask, jsonify, request

app = Flask(__name__)

# 内存中保存已接收的告警与截图关联记录（仅演示用）
RECEIVED: list[dict] = []
SCREENSHOTS: list[dict] = []
_SEQ = 0

# 后端摄像头列表（VideoCameraVO 子集），测试可通过直接修改此列表验证同步器
CAMERAS: list[dict] = []

# 与后端 AlarmService.create 对齐的校验规则
_VALID_SOURCE_TYPES = (1, 2, 3)   # 1-设备 / 2-环境 / 3-AI
_VALID_ALARM_LEVELS = (1, 2, 3)   # 1-预警 / 2-警报 / 3-控制


def _validate(data: dict) -> str | None:
    """校验 AlarmCreateDTO；返回错误描述，None 表示通过。"""
    project_id = data.get("projectId")
    if project_id is None or (isinstance(project_id, int) and project_id <= 0):
        return "projectId 必须为正整数"
    source_type = data.get("sourceType")
    if source_type not in _VALID_SOURCE_TYPES:
        return "sourceType 必须为 1-设备/2-环境/3-AI"
    alarm_level = data.get("alarmLevel")
    if alarm_level not in _VALID_ALARM_LEVELS:
        return "alarmLevel 必须为 1-预警/2-警报/3-控制"
    type_code = data.get("alarmTypeCode")
    if not type_code or len(type_code) > 30:
        return "alarmTypeCode 不能为空且长度不超过 30"
    content = data.get("alarmContent")
    if not content or len(content) > 500:
        return "alarmContent 不能为空且长度不超过 500"
    return None


@app.route("/api/alarms", methods=["POST"])
def receive_alert():
    """接收 AlarmCreateDTO 告警并回执（成功 code=0, data.id=新告警ID）。"""
    global _SEQ
    data = request.get_json(silent=True) or {}
    err = _validate(data)
    if err:
        print(f"\n[校验失败] {err} | payload={data}")
        return jsonify({"code": 400, "msg": f"参数校验失败: {err}", "data": None})

    _SEQ += 1
    RECEIVED.append(data)
    now = datetime.now().strftime("%H:%M:%S")
    print(
        f"\n[收到 #{_SEQ} {now}] "
        f"type={data.get('alarmTypeCode')} level={data.get('alarmLevel')} "
        f"conf={data.get('currentValue')} "
        f"project={data.get('projectId')} camera={data.get('cameraId')}"
    )
    print(f"  alarmContent: {data.get('alarmContent')}")
    print(f"  alarmTime: {data.get('alarmTime')}")
    return jsonify({"code": 0, "msg": "success", "data": {"id": _SEQ}})


@app.route("/api/alarms", methods=["GET"])
def stats():
    """探测/统计：健康探测 GET 到该地址返回 200 即视为后端在线。"""
    by_type: dict[str, int] = {}
    for item in RECEIVED:
        code = item.get("alarmTypeCode", "unknown")
        by_type[code] = by_type.get(code, 0) + 1
    return jsonify(
        {
            "code": 0,
            "msg": "success",
            "data": {
                "total": len(RECEIVED),
                "records": [],
                "by_type": by_type,
            },
        }
    )


@app.route("/api/ai/alarms/<int:alarm_id>/screenshot", methods=["POST"])
def link_screenshot(alarm_id):
    """关联 AI 告警截图（幂等）：成功 code=0, data.id=截图记录ID。"""
    if alarm_id < 1 or alarm_id > _SEQ:
        return jsonify({"code": 404, "msg": f"告警不存在: {alarm_id}", "data": None})
    data = request.get_json(silent=True) or {}
    url = (data.get("screenshotUrl") or "").strip()
    if not url:
        return jsonify({"code": 400, "msg": "screenshotUrl 不能为空", "data": None})
    for s in SCREENSHOTS:
        if s["alarm_id"] == alarm_id and s["url"] == url:
            return jsonify({"code": 0, "msg": "success", "data": {"id": s["id"]}})
    SCREENSHOTS.append({"id": len(SCREENSHOTS) + 1, "alarm_id": alarm_id, "url": url})
    print(f"[截图关联] alarm_id={alarm_id} url={url}")
    return jsonify({"code": 0, "msg": "success", "data": {"id": len(SCREENSHOTS)}})


@app.route("/api/video/cameras", methods=["GET"])
def list_cameras():
    """模拟后端摄像头分页列表（供 BackendCameraSyncer 同步）。"""
    try:
        page = max(1, int(request.args.get("pageNum", 1)))
        size = max(1, int(request.args.get("pageSize", 20)))
    except ValueError:
        return jsonify({"code": 400, "msg": "分页参数必须为数字", "data": None})
    start = (page - 1) * size
    rows = CAMERAS[start : start + size]
    total = len(CAMERAS)
    pages = (total + size - 1) // size
    return jsonify(
        {
            "code": 0,
            "msg": "success",
            "data": {
                "total": total,
                "pages": pages,
                "pageNum": page,
                "pageSize": size,
                "records": rows,
            },
        }
    )


@app.route("/api/auth/login", methods=["POST"])
def login():
    """模拟登录：username/password 非空即成功，返回 mock token。"""
    data = request.get_json(silent=True) or {}
    username = data.get("username")
    password = data.get("password")
    if not username or not password:
        return jsonify({"code": 401, "msg": "用户名或密码不能为空", "data": None})
    token = f"mock-token-{username}-{int(time.time() * 1000)}"
    print(f"[登录] {username} -> {token}")
    return jsonify({"code": 0, "msg": "success", "data": {"token": token}})


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="模拟真实后端告警接收接口")
    parser.add_argument("--host", default="0.0.0.0", help="监听地址")
    parser.add_argument("--port", type=int, default=8001, help="监听端口")
    args = parser.parse_args()

    print(f"模拟 Java 后端已启动: http://{args.host}:{args.port}")
    print(
        "接口: POST/GET /api/alarms, GET /api/video/cameras, "
        "POST /api/auth/login, POST /api/ai/alarms/<id>/screenshot"
    )
    app.run(host=args.host, port=args.port, debug=False)
