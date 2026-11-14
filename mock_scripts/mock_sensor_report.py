import argparse
import json
import math
import random
import socket
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from typing import Optional


DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 9090
LIST_SEPARATORS = ",;，；"


def split_values(raw_value):
    """拆分单值或列表，容忍中英文逗号/分号、多余空白和连续分隔符。"""
    normalized = (
        LIST_SEPARATORS[0].join(str(value) for value in raw_value)
        if isinstance(raw_value, (list, tuple))
        else str(raw_value)
    )
    for separator in LIST_SEPARATORS[1:]:
        normalized = normalized.replace(separator, LIST_SEPARATORS[0])
    return [part.strip() for part in normalized.split(LIST_SEPARATORS[0]) if part.strip()]


def parse_sensor_values(parser, option, raw_value, sensor_count, converter, allow_none=False):
    if raw_value is None and allow_none:
        return [None] * sensor_count
    parts = split_values(raw_value)
    if not parts:
        parser.error(f"{option} 不能为空")
    if len(parts) not in (1, sensor_count):
        parser.error(
            f"{option} 需要 1 个值（全部传感器共用）或 {sensor_count} 个值"
            f"（每个传感器一个），实际收到 {len(parts)} 个"
        )
    try:
        values = [converter(part) for part in parts]
    except (TypeError, ValueError) as exc:
        parser.error(f"{option} 包含无效值: {exc}")
    return values * sensor_count if len(values) == 1 else values


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="通过 TCP 循环模拟一个或多个传感器数据",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--host", default=DEFAULT_HOST, help="后端 TCP 地址")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help="后端 TCP 端口")
    parser.add_argument("--sensor-count", type=int, default=1, help="模拟的传感器数量")
    parser.add_argument(
        "--sensor-id",
        nargs="+",
        default="2",
        help="起始传感器 ID（自动递增），也可指定 ID 列表",
    )
    parser.add_argument("--metric-type", nargs="+", default="PM25", help="指标类型：单值或逗号/分号列表")
    parser.add_argument("--unit", nargs="+", default="ug/m3", help="指标单位：单值或逗号/分号列表")
    parser.add_argument("--min", dest="minimum", nargs="+", default="20.0", help="模拟值下限：单值或列表")
    parser.add_argument("--max", dest="maximum", nargs="+", default="120.0", help="模拟值上限：单值或列表")
    parser.add_argument(
        "--pattern",
        choices=("sine", "normal"),
        default="sine",
        help="数据变化模式：sine 正弦波（默认），normal 正态分布",
    )
    parser.add_argument("--period", type=int, default=30, help="正弦波周期包含的采样点数（默认 30）")
    parser.add_argument("--seed", type=int, default=42, help="正态分布随机种子（默认 42）")
    parser.add_argument("--interval", nargs="+", default="2.0", help="发送间隔（秒）：单值或列表")
    parser.add_argument("--precision", nargs="+", default="2", help="模拟值小数位数：单值或列表")
    parser.add_argument("--count", nargs="+", help="每个传感器最多发送次数：单值或列表；默认一直运行")
    parser.add_argument("--connect-timeout", nargs="+", default="5.0", help="连接超时（秒）：单值或列表")
    args = parser.parse_args(argv)
    if not 1 <= args.port <= 65535:
        parser.error("--port 必须在 1 到 65535 之间")
    if args.sensor_count <= 0:
        parser.error("--sensor-count 必须大于 0")

    raw_sensor_ids = split_values(args.sensor_id)
    if len(raw_sensor_ids) == 1:
        try:
            first_sensor_id = int(raw_sensor_ids[0])
        except ValueError as exc:
            parser.error(f"--sensor-id 包含无效值: {exc}")
        args.sensor_id = [first_sensor_id + index for index in range(args.sensor_count)]
    else:
        args.sensor_id = parse_sensor_values(
            parser, "--sensor-id", args.sensor_id, args.sensor_count, int
        )

    args.metric_type = parse_sensor_values(
        parser, "--metric-type", args.metric_type, args.sensor_count, str
    )
    args.unit = parse_sensor_values(parser, "--unit", args.unit, args.sensor_count, str)
    args.minimum = parse_sensor_values(parser, "--min", args.minimum, args.sensor_count, float)
    args.maximum = parse_sensor_values(parser, "--max", args.maximum, args.sensor_count, float)
    args.interval = parse_sensor_values(parser, "--interval", args.interval, args.sensor_count, float)
    args.precision = parse_sensor_values(parser, "--precision", args.precision, args.sensor_count, int)
    args.count = parse_sensor_values(
        parser, "--count", args.count, args.sensor_count, int, allow_none=True
    )
    args.connect_timeout = parse_sensor_values(
        parser, "--connect-timeout", args.connect_timeout, args.sensor_count, float
    )

    if len(set(args.sensor_id)) != len(args.sensor_id):
        parser.error("--sensor-id 中的传感器 ID 不能重复")
    for index in range(args.sensor_count):
        label = f"第 {index + 1} 个传感器（ID {args.sensor_id[index]}）"
        if not math.isfinite(args.minimum[index]) or not math.isfinite(args.maximum[index]):
            parser.error(f"{label}的 --min 和 --max 必须是有限数值")
        if args.minimum[index] > args.maximum[index]:
            parser.error(f"{label}的 --min 不能大于 --max")
        if (
            not math.isfinite(args.interval[index])
            or not math.isfinite(args.connect_timeout[index])
            or args.interval[index] <= 0
            or args.connect_timeout[index] <= 0
        ):
            parser.error(f"{label}的 --interval 和 --connect-timeout 必须大于 0")
        if args.precision[index] < 0:
            parser.error(f"{label}的 --precision 不能小于 0")
        if args.count[index] is not None and args.count[index] <= 0:
            parser.error(f"{label}的 --count 必须大于 0")
    if args.period <= 0:
        parser.error("--period 必须大于 0")
    return args


def esc_pressed():
    """Windows 控制台中检测 Esc；其他平台仍可使用 Ctrl+C 退出。"""
    if sys.platform != "win32":
        return False
    import msvcrt

    pressed = False
    while msvcrt.kbhit():
        if msvcrt.getwch() == "\x1b":
            pressed = True
    return pressed


def wait_or_escape(seconds):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if esc_pressed():
            return True
        time.sleep(min(0.05, max(0, deadline - time.monotonic())))
    return esc_pressed()


def connect(host, port, timeout):
    connection = socket.create_connection((host, port), timeout=timeout)
    connection.settimeout(timeout)
    stream = connection.makefile("rwb")
    send_line(stream, {"role": "sensor"})
    return connection, stream


def send_line(stream, payload):
    encoded = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    stream.write(encoded + b"\n")
    stream.flush()


def send_and_wait_ack(stream, payload):
    send_line(stream, payload)
    line = stream.readline()
    if not line:
        raise ConnectionError("后端已断开连接")
    response = json.loads(line.decode("utf-8"))
    if response.get("type") != "ack":
        raise ConnectionError(f"后端返回异常: {response}")


def close_connection(connection, stream):
    try:
        if stream is not None:
            stream.close()
    finally:
        if connection is not None:
            connection.close()


def generate_value(pattern, minimum, maximum, period, sample_index, rng):
    midpoint = (minimum + maximum) / 2
    if pattern == "sine":
        amplitude = (maximum - minimum) / 2
        value = midpoint + amplitude * math.sin(2 * math.pi * sample_index / period)
    else:
        standard_deviation = (maximum - minimum) / 6
        value = rng.gauss(midpoint, standard_deviation)
        value = min(maximum, max(minimum, value))
    return value


@dataclass
class SensorState:
    sensor_id: int
    metric_type: str
    unit: str
    minimum: float
    maximum: float
    interval: float
    precision: int
    count: Optional[int]
    connect_timeout: float
    rng: random.Random
    sent: int = 0
    succeeded: int = 0
    next_send_at: float = 0.0
    connection: object = None
    stream: object = None

    def finished(self):
        return self.count is not None and self.sent >= self.count


def build_sensor_states(args):
    return [
        SensorState(
            sensor_id=args.sensor_id[index],
            metric_type=args.metric_type[index],
            unit=args.unit[index],
            minimum=args.minimum[index],
            maximum=args.maximum[index],
            interval=args.interval[index],
            precision=args.precision[index],
            count=args.count[index],
            connect_timeout=args.connect_timeout[index],
            rng=random.Random(args.seed + index),
        )
        for index in range(args.sensor_count)
    ]


def main():
    args = parse_args()
    sensors = build_sensor_states(args)
    total_sent = 0
    print(f"TCP 地址: {args.host}:{args.port}")
    print(f"传感器数量: {args.sensor_count}，ID: {', '.join(str(item.sensor_id) for item in sensors)}")
    pattern_description = (
        f"正弦波（周期 {args.period} 个采样点）"
        if args.pattern == "sine"
        else f"正态分布（种子 {args.seed}）"
    )
    print(f"变化模式: {pattern_description}")
    for item in sensors:
        count_description = "不限" if item.count is None else str(item.count)
        print(
            f"  ID {item.sensor_id}: {item.metric_type} {item.unit}，"
            f"区间 [{item.minimum}, {item.maximum}]，间隔 {item.interval} 秒，"
            f"精度 {item.precision} 位，次数 {count_description}，"
            f"连接超时 {item.connect_timeout} 秒"
        )
    print("按 Esc 退出（非 Windows 终端请按 Ctrl+C）。")

    try:
        while not all(item.finished() for item in sensors):
            if esc_pressed():
                break
            now = time.monotonic()
            due_sensors = [
                item for item in sensors if not item.finished() and item.next_send_at <= now
            ]
            if not due_sensors:
                next_send_at = min(
                    item.next_send_at for item in sensors if not item.finished()
                )
                if wait_or_escape(max(0, next_send_at - now)):
                    break
                continue

            for item in due_sensors:
                value = round(
                    generate_value(
                        args.pattern,
                        item.minimum,
                        item.maximum,
                        args.period,
                        item.sent,
                        item.rng,
                    ),
                    item.precision,
                )
                payload = {
                    "monitorPointId": item.sensor_id,
                    "metricType": item.metric_type,
                    "metricValue": value,
                    "unit": item.unit,
                    "collectTime": datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3],
                }
                item.sent += 1
                total_sent += 1
                try:
                    if item.connection is None:
                        item.connection, item.stream = connect(
                            args.host, args.port, item.connect_timeout
                        )
                        print(f"ID {item.sensor_id} 已连接后端。")
                    send_and_wait_ack(item.stream, payload)
                    item.succeeded += 1
                    print(
                        f"[{total_sent:04d}][ID {item.sensor_id} #{item.sent:04d}] "
                        f"{payload['collectTime']}  {item.metric_type}={value} {item.unit}  成功"
                    )
                except (OSError, ValueError, ConnectionError) as exc:
                    print(
                        f"[{total_sent:04d}][ID {item.sensor_id} #{item.sent:04d}] "
                        f"发送失败: {exc}；下次发送时重连"
                    )
                    try:
                        close_connection(item.connection, item.stream)
                    except OSError:
                        pass
                    item.connection = None
                    item.stream = None
                item.next_send_at = time.monotonic() + item.interval

            if esc_pressed():
                break
    except KeyboardInterrupt:
        pass
    finally:
        for item in sensors:
            try:
                close_connection(item.connection, item.stream)
            except OSError:
                pass

    total_succeeded = sum(item.succeeded for item in sensors)
    print(
        f"\n模拟结束：{len(sensors)} 个传感器，共发送 {total_sent} 次，"
        f"成功 {total_succeeded} 次，失败 {total_sent - total_succeeded} 次。"
    )
    for item in sensors:
        print(
            f"  ID {item.sensor_id}: 发送 {item.sent} 次，成功 {item.succeeded} 次，"
            f"失败 {item.sent - item.succeeded} 次"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
