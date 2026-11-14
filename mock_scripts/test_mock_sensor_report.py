import contextlib
import io
import json
import socketserver
import sys
import threading
import unittest
from unittest import mock

from mock_scripts.mock_sensor_report import build_sensor_states, main, parse_args


class ParseArgsTest(unittest.TestCase):
    def test_single_values_are_broadcast_and_sensor_ids_increment(self):
        args = parse_args(
            [
                "--sensor-count",
                "3",
                "--sensor-id",
                "10",
                "--min",
                "5",
                "--max=9",
                "--count",
                "2",
            ]
        )

        self.assertEqual([10, 11, 12], args.sensor_id)
        self.assertEqual([5.0, 5.0, 5.0], args.minimum)
        self.assertEqual([9.0, 9.0, 9.0], args.maximum)
        self.assertEqual([2, 2, 2], args.count)

    def test_per_sensor_values_accept_mixed_chinese_and_english_separators(self):
        args = parse_args(
            [
                "--sensor-count=5",
                "--sensor-id=10, 20;30，40；50",
                "--metric-type=PM25; PM10，TEMP,HUMIDITY；NOISE",
                "--unit=u1,u2,u3,u4,u5",
                "--min=5,4;3，7；6",
                "--max=10;11;12;13;14",
                "--interval=1,2,3,4,5",
                "--precision=0;1;2;3;4",
                "--count=1,2,3,4,5",
                "--connect-timeout=2;3;4;5;6",
            ]
        )

        self.assertEqual([10, 20, 30, 40, 50], args.sensor_id)
        self.assertEqual([5.0, 4.0, 3.0, 7.0, 6.0], args.minimum)
        self.assertEqual(["PM25", "PM10", "TEMP", "HUMIDITY", "NOISE"], args.metric_type)
        self.assertEqual([1.0, 2.0, 3.0, 4.0, 5.0], args.interval)
        self.assertEqual([0, 1, 2, 3, 4], args.precision)
        self.assertEqual([1, 2, 3, 4, 5], args.count)

        states = build_sensor_states(args)
        self.assertEqual(5, len(states))
        self.assertEqual(50, states[-1].sensor_id)
        self.assertEqual(6.0, states[-1].minimum)

    def test_unquoted_spaces_between_list_items_are_tolerated(self):
        args = parse_args(
            ["--sensor-count", "3", "--min", "5,", "4;", "3", "--max", "10"]
        )

        self.assertEqual([5.0, 4.0, 3.0], args.minimum)

    def test_wrong_list_length_is_rejected(self):
        with contextlib.redirect_stderr(io.StringIO()) as error_output:
            with self.assertRaises(SystemExit):
                parse_args(["--sensor-count", "5", "--min", "1,2"])

        self.assertIn("需要 1 个值", error_output.getvalue())
        self.assertIn("或 5 个值", error_output.getvalue())

    def test_duplicate_sensor_ids_are_rejected(self):
        with contextlib.redirect_stderr(io.StringIO()) as error_output:
            with self.assertRaises(SystemExit):
                parse_args(["--sensor-count", "2", "--sensor-id", "7;7"])

        self.assertIn("不能重复", error_output.getvalue())


class SensorHandler(socketserver.StreamRequestHandler):
    def handle(self):
        role = json.loads(self.rfile.readline().decode("utf-8"))
        if role != {"role": "sensor"}:
            return
        for line in self.rfile:
            self.server.reports.append(json.loads(line.decode("utf-8")))
            self.wfile.write(b'{"type":"ack"}\n')
            self.wfile.flush()


class SensorServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


class MainIntegrationTest(unittest.TestCase):
    def test_multiple_sensors_send_independently_to_one_server(self):
        server = SensorServer(("127.0.0.1", 0), SensorHandler)
        server.reports = []
        server_thread = threading.Thread(target=server.serve_forever, daemon=True)
        server_thread.start()
        argv = [
            "mock_sensor_report.py",
            "--port",
            str(server.server_address[1]),
            "--sensor-count",
            "2",
            "--sensor-id",
            "100",
            "--min",
            "1;10",
            "--max",
            "2;20",
            "--interval",
            "0.01;0.02",
            "--count",
            "2",
        ]

        try:
            output = io.StringIO()
            with mock.patch.object(sys, "argv", argv), contextlib.redirect_stdout(output):
                self.assertEqual(0, main())
        finally:
            server.shutdown()
            server.server_close()
            server_thread.join(timeout=2)

        self.assertEqual(4, len(server.reports))
        self.assertEqual([100, 101], sorted({item["monitorPointId"] for item in server.reports}))
        self.assertEqual(2, sum(item["monitorPointId"] == 100 for item in server.reports))
        self.assertEqual(2, sum(item["monitorPointId"] == 101 for item in server.reports))
        self.assertIn("传感器数量: 2", output.getvalue())
        self.assertIn("模拟结束：2 个传感器", output.getvalue())


if __name__ == "__main__":
    unittest.main()
