# -*- coding: utf-8 -*-
"""订阅并自动验收智慧物流 MQTT 数据流。"""
import argparse
import collections
import datetime
import json
import pathlib
import re
import threading
import time

import paho.mqtt.client as mqtt

from mqtt_credentials import load_mqtt_credentials


VEHICLE_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
TRANSPORT_STATUSES = {"待装货", "已装货", "运输中", "已送达"}
ALERT_TYPES = {"偏航", "异常停留", "异常开箱"}
ALERT_SOURCES = {"simulator", "backend", "device"}


def parse_timestamp(value):
    if not isinstance(value, str):
        raise ValueError("timestamp 必须是字符串")
    return datetime.datetime.fromisoformat(value.replace("Z", "+00:00"))


def validate_common(payload):
    errors = []
    if payload.get("schema_version") != "1.0":
        errors.append("schema_version 必须为 1.0")
    vehicle_id = payload.get("vehicle_id")
    if not isinstance(vehicle_id, str) or not VEHICLE_ID_PATTERN.fullmatch(vehicle_id):
        errors.append("vehicle_id 格式错误")
    try:
        parse_timestamp(payload.get("timestamp"))
    except (TypeError, ValueError):
        errors.append("timestamp 不是有效 ISO 8601 时间")
    return errors


def validate_gps(topic, payload):
    errors = validate_common(payload)
    required = {"lat", "lon", "speed_kmh", "heading", "transport_status", "coordinate_system"}
    missing = sorted(required - set(payload))
    if missing:
        errors.append("缺少字段：" + ", ".join(missing))
        return errors
    if not isinstance(payload["lat"], (int, float)) or not -90 <= payload["lat"] <= 90:
        errors.append("lat 超出范围")
    if not isinstance(payload["lon"], (int, float)) or not -180 <= payload["lon"] <= 180:
        errors.append("lon 超出范围")
    if not isinstance(payload["speed_kmh"], (int, float)) or payload["speed_kmh"] < 0:
        errors.append("speed_kmh 必须为非负数")
    if not isinstance(payload["heading"], (int, float)) or not 0 <= payload["heading"] < 360:
        errors.append("heading 必须在 [0, 360) 范围")
    if payload["transport_status"] not in TRANSPORT_STATUSES:
        errors.append("transport_status 枚举值错误")
    if payload["coordinate_system"] != "WGS84":
        errors.append("coordinate_system 必须为 WGS84")
    expected_suffix = f"/vehicle/{payload.get('vehicle_id')}/gps"
    if not topic.endswith(expected_suffix):
        errors.append("主题中的 vehicle_id 与 payload 不一致")
    return errors


def validate_status(topic, payload):
    errors = validate_common(payload)
    if not isinstance(payload.get("online"), bool):
        errors.append("online 必须是布尔值")
    if payload.get("transport_status") not in TRANSPORT_STATUSES:
        errors.append("transport_status 枚举值错误")
    expected_suffix = f"/vehicle/{payload.get('vehicle_id')}/status"
    if not topic.endswith(expected_suffix):
        errors.append("主题中的 vehicle_id 与 payload 不一致")
    return errors


def validate_alert(payload):
    errors = validate_common(payload)
    if payload.get("alert_type") not in ALERT_TYPES:
        errors.append("alert_type 枚举值错误")
    if not isinstance(payload.get("description"), str) or not payload.get("description"):
        errors.append("description 不能为空")
    if payload.get("source") not in ALERT_SOURCES:
        errors.append("source 枚举值错误")
    return errors


def main():
    parser = argparse.ArgumentParser(description="自动验收智慧物流 MQTT 数据流")
    parser.add_argument("--host", default="localhost", help="MQTT Broker 地址")
    parser.add_argument("--port", type=int, default=1883, help="MQTT Broker 端口")
    parser.add_argument("--credentials", type=pathlib.Path,
                        help="MQTT 凭据 env 文件；自动读取云端地址、端口和账号密码")
    parser.add_argument("--username", help="MQTT 用户名")
    parser.add_argument("--password", help="MQTT 密码（推荐改用 --credentials）")
    parser.add_argument("--prefix", default="iot/carla", help="MQTT 主题前缀")
    parser.add_argument("--duration", type=float, default=10.0, help="验收秒数")
    parser.add_argument("--expected-vehicles", type=int, default=5,
                        help="期望至少出现的车辆数")
    parser.add_argument("--min-rate", type=float, default=0.8,
                        help="每车 GPS 最低接收频率 Hz")
    parser.add_argument("--require-alerts", type=int, default=0,
                        help="至少需要收到的告警数")
    parser.add_argument("--report", help="可选：写入 JSON 验收报告")
    args = parser.parse_args()

    if args.credentials:
        credentials = load_mqtt_credentials(args.credentials)
        if args.host == "localhost":
            args.host = credentials["MQTT_HOST"]
        if args.port == 1883:
            args.port = int(credentials["MQTT_EXTERNAL_PORT"])
        args.username = args.username or credentials["MQTT_USERNAME"]
        args.password = args.password or credentials["MQTT_PASSWORD"]
    if bool(args.username) != bool(args.password):
        parser.error("--username 和 --password 必须同时提供")

    if args.duration <= 0:
        parser.error("--duration 必须大于 0")

    connected = threading.Event()
    gps_received = collections.defaultdict(list)
    message_counts = collections.Counter()
    alerts = []
    errors = []
    last_payload_time = {}
    lock = threading.Lock()

    def on_connect(client, userdata, flags, reason_code, properties):
        if reason_code == 0:
            client.subscribe(f"{args.prefix}/#", qos=1)
            connected.set()
        else:
            errors.append(f"Broker 拒绝连接：{reason_code}")

    def on_message(client, userdata, message):
        received_at = time.monotonic()
        with lock:
            message_counts[message.topic] += 1
            try:
                payload = json.loads(message.payload.decode("utf-8"))
                if not isinstance(payload, dict):
                    raise ValueError("payload 不是 JSON 对象")
            except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as exc:
                errors.append(f"{message.topic}: JSON 解析失败：{exc}")
                return

            if message.topic.endswith("/gps"):
                validation_errors = validate_gps(message.topic, payload)
                vehicle_id = payload.get("vehicle_id", "<unknown>")
                gps_received[vehicle_id].append(received_at)
                try:
                    payload_time = parse_timestamp(payload.get("timestamp"))
                    previous = last_payload_time.get(vehicle_id)
                    if previous is not None and payload_time < previous:
                        validation_errors.append("timestamp 出现倒退")
                    last_payload_time[vehicle_id] = payload_time
                except (TypeError, ValueError):
                    pass
            elif message.topic.endswith("/status"):
                validation_errors = validate_status(message.topic, payload)
            elif message.topic == f"{args.prefix}/alert":
                validation_errors = validate_alert(payload)
                alerts.append(payload)
            else:
                validation_errors = ["未知主题"]

            errors.extend(f"{message.topic}: {item}" for item in validation_errors)

    client = mqtt.Client(
        mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"smart-logistics-verify-{int(time.time())}",
    )
    if args.username:
        client.username_pw_set(args.username, args.password)
    client.on_connect = on_connect
    client.on_message = on_message
    client.connect(args.host, args.port, keepalive=30)
    client.loop_start()
    try:
        if not connected.wait(timeout=5):
            raise TimeoutError("等待 MQTT CONNACK 超时")
        print(f"[验收] 订阅 {args.prefix}/#，持续 {args.duration:.1f} 秒")
        time.sleep(args.duration)
    finally:
        client.disconnect()
        client.loop_stop()

    rates = {}
    for vehicle_id, received_times in gps_received.items():
        if len(received_times) < 2:
            rates[vehicle_id] = 0.0
        else:
            span = received_times[-1] - received_times[0]
            rates[vehicle_id] = 0.0 if span <= 0 else (len(received_times) - 1) / span

    checks = {
        "schema_valid": not errors,
        "vehicle_count": len(gps_received) >= args.expected_vehicles,
        "gps_rate": len(gps_received) >= args.expected_vehicles and all(
            rate >= args.min_rate for rate in rates.values()
        ),
        "alert_count": len(alerts) >= args.require_alerts,
    }
    report = {
        "passed": all(checks.values()),
        "checks": checks,
        "expected_vehicles": args.expected_vehicles,
        "observed_vehicles": sorted(gps_received),
        "gps_rates_hz": {key: round(value, 3) for key, value in sorted(rates.items())},
        "alert_count": len(alerts),
        "message_counts": dict(sorted(message_counts.items())),
        "errors": errors,
    }

    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    print(rendered)
    if args.report:
        report_path = pathlib.Path(args.report)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(rendered + "\n", encoding="utf-8")
        print(f"[报告] {report_path.resolve()}")
    raise SystemExit(0 if report["passed"] else 1)


if __name__ == "__main__":
    main()
