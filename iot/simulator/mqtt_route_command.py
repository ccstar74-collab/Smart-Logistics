# -*- coding: utf-8 -*-
"""Publish a route-change command and wait for the simulated vehicle ACK."""

import argparse
import datetime
import json
import threading
import time

import paho.mqtt.client as mqtt


def now_iso():
    now = datetime.datetime.now(datetime.timezone.utc)
    return now.isoformat(timespec="milliseconds").replace("+00:00", "Z")


def parse_waypoints(text):
    waypoints = []
    try:
        for item in text.split(";"):
            lon, lat = (float(value) for value in item.split(","))
            if not -180 <= lon <= 180 or not -90 <= lat <= 90:
                raise ValueError("经纬度越界")
            waypoints.append({"lon": lon, "lat": lat})
    except ValueError as exc:
        raise ValueError("途经点格式应为 经度,纬度;经度,纬度") from exc
    if len(waypoints) < 2:
        raise ValueError("至少需要2个途经点")
    if len(waypoints) > 20:
        raise ValueError("途经点不能超过20个")
    return waypoints


def main():
    parser = argparse.ArgumentParser(description="下发路线调整指令并等待模拟车辆ACK")
    parser.add_argument("--vehicle", default="sim_000", help="目标车辆deviceCode")
    parser.add_argument(
        "--waypoints",
        default="106.750000,29.613500;106.770000,29.614500;106.790000,29.615000",
        help="WGS84途经点：经度,纬度;经度,纬度",
    )
    parser.add_argument("--command-id", help="幂等指令ID；默认自动生成")
    parser.add_argument("--task-id", type=int, help="可选的数据库运输任务ID")
    parser.add_argument("--host", default="localhost", help="MQTT Broker地址")
    parser.add_argument("--port", type=int, default=1883, help="MQTT Broker端口")
    parser.add_argument("--prefix", default="iot/carla", help="MQTT主题前缀")
    parser.add_argument("--qos", type=int, choices=(0, 1, 2), default=1)
    parser.add_argument("--timeout", type=float, default=30, help="等待ACK秒数")
    args = parser.parse_args()

    if args.timeout <= 0:
        parser.error("--timeout必须大于0")
    if args.task_id is not None and args.task_id <= 0:
        parser.error("--task-id必须大于0")
    try:
        waypoints = parse_waypoints(args.waypoints)
    except ValueError as exc:
        parser.error(str(exc))

    command_id = args.command_id or f"CMD_{datetime.datetime.now():%Y%m%d_%H%M%S_%f}"
    command_topic = f"{args.prefix}/vehicle/{args.vehicle}/command"
    ack_topic = f"{args.prefix}/vehicle/{args.vehicle}/command/ack"
    command = {
        "schema_version": "1.0",
        "command_id": command_id,
        "vehicle_id": args.vehicle,
        "command_type": "ROUTE_CHANGE",
        "route": {
            "coordinate_system": "WGS84",
            "waypoints": waypoints,
        },
        "timestamp": now_iso(),
    }
    if args.task_id is not None:
        command["task_id"] = args.task_id

    connected = threading.Event()
    ack_received = threading.Event()
    ack_holder = {}
    client = mqtt.Client(
        mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"smart-logistics-command-probe-{int(time.time() * 1000)}",
    )

    def on_connect(client_obj, userdata, flags, reason_code, properties):
        if reason_code == 0:
            client_obj.subscribe(ack_topic, qos=args.qos)
            connected.set()
        else:
            print(f"[错误] Broker拒绝连接：{reason_code}")

    def on_message(client_obj, userdata, message):
        try:
            payload = json.loads(message.payload.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            return
        if payload.get("command_id") == command_id:
            ack_holder.update(payload)
            ack_received.set()

    client.on_connect = on_connect
    client.on_message = on_message
    client.connect(args.host, args.port, keepalive=30)
    client.loop_start()
    try:
        if not connected.wait(timeout=5):
            raise TimeoutError("等待MQTT连接超时")
        # 给Broker短暂时间完成ACK Topic订阅，避免极快回执发生在SUBACK之前。
        time.sleep(0.2)
        info = client.publish(
            command_topic,
            json.dumps(command, ensure_ascii=False, separators=(",", ":")),
            qos=args.qos,
            retain=False,
        )
        info.wait_for_publish(timeout=3)
        print(f"[已发送] Topic={command_topic}")
        print(json.dumps(command, ensure_ascii=False, indent=2))
        if not ack_received.wait(timeout=args.timeout):
            raise TimeoutError(f"{args.timeout:g}秒内未收到ACK：{ack_topic}")
        print(f"[收到ACK] Topic={ack_topic}")
        print(json.dumps(ack_holder, ensure_ascii=False, indent=2))
        if ack_holder.get("status") != "EXECUTED":
            raise RuntimeError("车辆返回执行失败")
    finally:
        client.disconnect()
        client.loop_stop()


if __name__ == "__main__":
    main()
