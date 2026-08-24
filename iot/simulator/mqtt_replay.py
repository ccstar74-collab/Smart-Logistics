# -*- coding: utf-8 -*-
"""回放 samples 目录中的 MQTT JSONL 数据。"""
import argparse
import datetime
import json
import pathlib
import threading
import time

import paho.mqtt.client as mqtt

from mqtt_credentials import load_mqtt_credentials


def now_iso():
    now = datetime.datetime.now(datetime.timezone.utc)
    return now.isoformat(timespec="milliseconds").replace("+00:00", "Z")


def read_events(path):
    events = []
    with path.open("r", encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, start=1):
            if not line.strip():
                continue
            event = json.loads(line)
            if "topic" not in event or "payload" not in event:
                raise ValueError(f"第 {line_number} 行缺少 topic 或 payload")
            if not isinstance(event["payload"], dict):
                raise ValueError(f"第 {line_number} 行 payload 必须是 JSON 对象")
            events.append(event)
    if not events:
        raise ValueError("输入文件没有可回放事件")
    return events


def main():
    parser = argparse.ArgumentParser(description="回放智慧物流 MQTT JSONL 数据")
    parser.add_argument("input", help="输入 JSONL 文件")
    parser.add_argument("--host", default="localhost", help="MQTT Broker 地址")
    parser.add_argument("--port", type=int, default=1883, help="MQTT Broker 端口")
    parser.add_argument("--credentials", type=pathlib.Path,
                        help="MQTT 凭据 env 文件；自动读取云端地址、端口和账号密码")
    parser.add_argument("--username", help="MQTT 用户名")
    parser.add_argument("--password", help="MQTT 密码（推荐改用 --credentials）")
    parser.add_argument("--qos", type=int, choices=(0, 1, 2), default=1)
    parser.add_argument("--speed", type=float, default=1.0,
                        help="回放倍速；2 表示等待时间减半")
    parser.add_argument("--repeat", type=int, default=1, help="重复次数")
    parser.add_argument("--preserve-timestamps", action="store_true",
                        help="保留样例时间戳；默认改为当前 UTC 时间")
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

    if args.speed <= 0:
        parser.error("--speed 必须大于 0")
    if args.repeat <= 0:
        parser.error("--repeat 必须大于 0")

    source = pathlib.Path(args.input)
    events = read_events(source)
    connected = threading.Event()

    def on_connect(client, userdata, flags, reason_code, properties):
        if reason_code == 0:
            connected.set()
        else:
            print(f"[错误] Broker 拒绝连接：{reason_code}")

    client = mqtt.Client(
        mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"smart-logistics-replay-{int(time.time())}",
    )
    if args.username:
        client.username_pw_set(args.username, args.password)
    client.on_connect = on_connect
    client.connect(args.host, args.port, keepalive=30)
    client.loop_start()
    if not connected.wait(timeout=5):
        client.disconnect()
        client.loop_stop()
        raise TimeoutError("等待 MQTT CONNACK 超时")

    count = 0
    started = time.monotonic()
    try:
        for round_number in range(1, args.repeat + 1):
            for event in events:
                delay = max(0.0, float(event.get("delay_ms", 0))) / 1000.0 / args.speed
                if delay:
                    time.sleep(delay)
                payload = dict(event["payload"])
                if not args.preserve_timestamps and "timestamp" in payload:
                    payload["timestamp"] = now_iso()
                info = client.publish(
                    event["topic"],
                    json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
                    qos=args.qos,
                    retain=bool(event.get("retain", False)),
                )
                if info.rc != mqtt.MQTT_ERR_SUCCESS:
                    raise RuntimeError(f"发布失败：topic={event['topic']}, rc={info.rc}")
                info.wait_for_publish(timeout=3)
                count += 1
                print(f"[回放 {round_number}/{args.repeat}] {event['topic']}")
    finally:
        client.disconnect()
        client.loop_stop()

    elapsed = time.monotonic() - started
    print(f"[完成] {count} 条消息，耗时 {elapsed:.2f} 秒")


if __name__ == "__main__":
    main()
