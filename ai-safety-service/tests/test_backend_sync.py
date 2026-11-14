"""
后端摄像头同步器测试（不依赖 cv2/YOLO 模型、pytest/flask）。

后端接口用标准库 http.server 在内存线程中模拟，配合 FakeManager
验证 BackendCameraSyncer 的拉取与增删逻辑：
    1. 首次同步：后端新增摄像头 -> start_camera 被调用且配置正确；
    2. 后端删除摄像头 -> stop_camera 被调用；
    3. 后端空列表 -> 不误删本地监测；
    4. yaml 已启动（backend_camera_id 匹配）-> 跳过重复启动；
    5. 后端不可达 -> 返回空结果，不抛异常。

运行（需已安装 requests，项目 .venv 已含）：
    .venv\\Scripts\\python.exe tests\\test_backend_sync.py -v
"""

from __future__ import annotations

import json
import sys
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse, parse_qs

ROOT = Path(__file__).resolve().parent.parent
TESTS = Path(__file__).resolve().parent
for p in (str(ROOT), str(TESTS)):
    if p not in sys.path:
        sys.path.insert(0, p)

from app.backend_sync import BackendCameraSyncer  # noqa: E402
from app.config import settings  # noqa: E402

# 后端摄像头列表（VideoCameraVO 子集），测试用例直接修改
CAMERAS: list[dict] = []


class _Handler(BaseHTTPRequestHandler):
    """极简后端模拟：GET /api/video/cameras 返回分页列表。"""

    def do_GET(self):  # noqa: N802
        parsed = urlparse(self.path)
        if parsed.path != "/api/video/cameras":
            self._send(404, {"code": 404, "msg": "not found", "data": None})
            return
        qs = parse_qs(parsed.query)
        try:
            page = max(1, int(qs.get("pageNum", ["1"])[0]))
            size = max(1, int(qs.get("pageSize", ["20"])[0]))
        except ValueError:
            self._send(400, {"code": 400, "msg": "参数非法", "data": None})
            return
        start = (page - 1) * size
        rows = CAMERAS[start : start + size]
        total = len(CAMERAS)
        pages = (total + size - 1) // size
        self._send(
            200,
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
            },
        )

    def _send(self, status: int, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):  # noqa: A003
        pass


class FakeManager:
    """记录 start/stop 调用的摄像头管理器替身（不触发真实监测线程）。"""

    def __init__(self) -> None:
        self.started: list[dict] = []
        self.stopped: list[str] = []
        self.monitors: dict[str, object] = {}

    def _register(self, config: dict) -> None:
        monitor = type(
            "FakeMonitor",
            (),
            {
                "camera_id": config["camera_id"],
                "backend_camera_id": config.get("backend_camera_id"),
            },
        )()
        self.monitors[config["camera_id"]] = monitor

    def start_camera(self, config: dict) -> dict:
        self.started.append(dict(config))
        self._register(config)
        return {"camera_id": config["camera_id"], "state": "running"}

    def stop_camera(self, camera_id: str) -> bool:
        if camera_id not in self.monitors:
            return False
        self.stopped.append(camera_id)
        del self.monitors[camera_id]
        return True

    def get_status(self) -> list[dict]:
        return [{"camera_id": cid, "state": "running"} for cid in self.monitors]

    def get_monitor(self, camera_id: str):
        return self.monitors.get(camera_id)


class BackendSyncTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls._server = ThreadingHTTPServer(("127.0.0.1", 0), _Handler)
        cls._port = cls._server.server_address[1]
        cls._thread = threading.Thread(
            target=cls._server.serve_forever, daemon=True
        )
        cls._thread.start()
        cls.base_url = f"http://127.0.0.1:{cls._port}/api"

    @classmethod
    def tearDownClass(cls) -> None:
        cls._server.shutdown()
        cls._server.server_close()
        cls._thread.join(timeout=5)

    def setUp(self) -> None:
        CAMERAS.clear()
        self._old_base = settings.backend_sync_base_url
        settings.backend_sync_base_url = self.base_url
        self.mgr = FakeManager()

    def tearDown(self) -> None:
        settings.backend_sync_base_url = self._old_base

    def _syncer(self):
        return BackendCameraSyncer(self.mgr, interval=3600)

    @staticmethod
    def _cam(cid, stream="rtsp://127.0.0.1/live/cam", name=None):
        return {"id": cid, "cameraName": name or f"摄像头{cid}", "streamUrl": stream}

    # ------------------------------------------------------------------ #
    def test_first_sync_starts_cameras(self):
        CAMERAS.extend([self._cam(1, stream="rtsp://x/1"), self._cam(2, stream="rtsp://x/2")])
        result = self._syncer().sync_once()
        self.assertEqual(result["added"], ["BE-1", "BE-2"])
        self.assertEqual(len(self.mgr.started), 2)
        cfg = self.mgr.started[0]
        self.assertEqual(cfg["camera_id"], "BE-1")
        self.assertEqual(cfg["backend_camera_id"], 1)
        self.assertEqual(cfg["rtsp_url"], "rtsp://x/1")
        self.assertEqual(cfg["java_endpoint"], f"{self.base_url}/alarms")
        self.assertEqual(
            cfg["screenshot_endpoint"], f"{self.base_url}/ai/alarms/{{alarm_id}}/screenshot"
        )
        self.assertTrue(
            cfg["screenshot_base_url"].startswith(
                f"http://127.0.0.1:{settings.port}/screenshots/BE-1"
            )
        )

    def test_removed_camera_stops_local(self):
        CAMERAS.extend([self._cam(1, stream="rtsp://x/1")])
        syncer = self._syncer()
        syncer.sync_once()
        self.assertEqual(len(self.mgr.started), 1)

        # 后端改为只有摄像头 2：1 应被停止，2 被启动
        CAMERAS.clear()
        CAMERAS.extend([self._cam(2, stream="rtsp://x/2")])
        result = syncer.sync_once()
        self.assertEqual(result["removed"], ["BE-1"])
        self.assertEqual(result["added"], ["BE-2"])
        self.assertEqual(self.mgr.stopped, ["BE-1"])
        self.assertNotIn("BE-1", self.mgr.monitors)
        self.assertIn("BE-2", self.mgr.monitors)

    def test_empty_list_no_destructive_change(self):
        CAMERAS.extend([self._cam(1, stream="rtsp://x/1")])
        syncer = self._syncer()
        syncer.sync_once()
        self.assertIn("BE-1", self.mgr.monitors)

        CAMERAS.clear()
        result = syncer.sync_once()
        self.assertEqual(result, {"added": [], "removed": []})
        self.assertIn("BE-1", self.mgr.monitors)  # 空列表不误删

    def test_yaml_instance_skips_duplicate(self):
        CAMERAS.extend([self._cam(1, stream="rtsp://x/1")])
        # 模拟 yaml 已启动一路 backend_camera_id=1 的监测
        self.mgr.start_camera(
            {
                "camera_id": "CAM_MOCK",
                "camera_name": "yaml摄像头",
                "rtsp_url": "rtsp://yaml/1",
                "backend_camera_id": 1,
            }
        )
        syncer = self._syncer()
        result = syncer.sync_once()
        self.assertEqual(result["added"], [])
        self.assertIsNone(syncer._synced.get(1))  # 登记为 yaml 覆盖，不起 BE-1
        self.assertNotIn("BE-1", self.mgr.monitors)

    def test_backend_unreachable_returns_empty(self):
        CAMERAS.extend([self._cam(1, stream="rtsp://x/1")])
        settings.backend_sync_base_url = "http://127.0.0.1:59999/api"
        result = BackendCameraSyncer(self.mgr, interval=3600).sync_once()
        self.assertEqual(result, {"added": [], "removed": []})


if __name__ == "__main__":
    unittest.main(verbosity=2)
