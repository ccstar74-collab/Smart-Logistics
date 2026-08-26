# -*- coding: utf-8 -*-
"""Publish a backend-owned task-route notification and wait for simulator ACK."""

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


def main():
    parser = argparse.ArgumentParser(description="发布TASK_ROUTE_READY通知并等待模拟车辆ACK")
    parser.add_argument("--vehicle", required=True, help="vehicle.sim_code，例如sim_000")
    parser.add_argument("--task-id", required=True, type=int, help="运输任务ID")
    parser.add_argument("--route-id", required=True, help="业务后端生成的路线ID")
    parser.add_argument("--route-version", type=int, default=1, help="路线版本，默认1")
    parser.add_argument("--command-id", help="幂等指令ID；默认自动生成")
    parser.add_argument("--host", default="localhost", help="MQTT Broker地址")
    parser.add_argument("--port", type=int, default=1883, help="MQTT Broker端口")
    parser.add_argument("--credentials", type=pathlib.Path, help="MQTT凭据env文件")
    parser.add_argument("--username", help="MQTT用户名")
    parser.add_argument("--password", help="MQTT密码（推荐改用--credentials）")
    parser.add_argument("--prefix", default="iot/carla", help="MQTT主题前缀")
    parser.add_argument("--qos", type=int, choices=(0, 1, 2), default=1)
    parser.add_argument("--timeout", type=float, default=30, help="等待ACK秒数")
    args = parser.parse_args()

    if args.credentials:
        credentials = load_mqtt_credentials(args.credentials)
        if args.host == "localhost":
            args.host = credentials["MQTT_HOST"]
        if args.port == 1883:
            args.port = int(credentials["MQTT_EXTERNAL_PORT"])
        args.username = args.username or credentials["MQTT_USERNAME"]
        args.password = args.password or credentials["MQTT_PASSWORD"]
    if args.task_id <= 0:
        parser.error("--task-id必须大于0")
    if args.route_version <= 0:
        parser.error("--route-version必须大于0")
    if args.timeout <= 0:
        parser.error("--timeout必须大于0")

    command_id = args.command_id or f"CMD_ROUTE_{args.task_id}_V{args.route_version}_{int(time.time())}"
    command_topic = f"{args.prefix}/vehicle/{args.vehicle}/command"
    ack_topic = f"{args.prefix}/vehicle/{args.vehicle}/command/ack"
    command = {
        "schema_version": "1.0",
        "command_id": command_id,
        "vehicle_id": args.vehicle,
        "task_id": args.task_id,
        "route_id": args.route_id,
        "route_version": args.route_version,
        "command_type": "TASK_ROUTE_READY",
        "timestamp": now_iso(),
    }

    connected = threading.Event()
    ack_received = threading.Event()
    ack_holder = {}
    client = mqtt.Client(
        mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"smart-logistics-route-notifier-{int(time.time() * 1000)}",
    )
    if args.username:
        client.username_pw_set(args.username, args.password)

    def on_connect(client_obj, userdata, flags, reason_code, properties):
        if reason_code == 0:
            client_obj.subscribe(ack_topic, qos=args.qos)
            connected.set()
        else:
            print(f"[ERROR] Broker拒绝连接：{reason_code}")

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
        time.sleep(0.2)
        info = client.publish(
            command_topic,
            json.dumps(command, ensure_ascii=False, separators=(",", ":")),
            qos=args.qos,
            retain=False,
        )
        info.wait_for_publish(timeout=3)
        print(f"[COMMAND_SENT] Topic={command_topic}")
        print(json.dumps(command, ensure_ascii=False, indent=2))
        if not ack_received.wait(timeout=args.timeout):
            raise TimeoutError(f"{args.timeout:g}秒内未收到ACK：{ack_topic}")
        print(f"[ACK_RECEIVED] Topic={ack_topic}")
        print(json.dumps(ack_holder, ensure_ascii=False, indent=2))
        if ack_holder.get("status") != "EXECUTED":
            raise RuntimeError("模拟车辆返回执行失败")
    finally:
        client.disconnect()
        client.loop_stop()


if __name__ == "__main__":
    main()
