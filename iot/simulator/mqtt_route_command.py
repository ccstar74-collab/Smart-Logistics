# -*- coding: utf-8 -*-
"""Publish a route-change command and wait for the simulated vehicle ACK."""

import argparse
import datetime
import json
import os
import pathlib
import threading
import time

import paho.mqtt.client as mqtt

from mqtt_credentials import load_mqtt_credentials
from task_route import fetch_task_route_context


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
    parser.add_argument("--vehicle", help="目标车辆deviceCode；可覆盖任务接口返回值")
    parser.add_argument(
        "--waypoints",
        default=None,
        help="WGS84途经点：经度,纬度;经度,纬度",
    )
    parser.add_argument("--task-url", help="业务后端任务路线上下文接口完整URL")
    parser.add_argument("--command-id", help="幂等指令ID；默认自动生成")
    parser.add_argument("--task-id", type=int, help="可选的数据库运输任务ID")
    parser.add_argument("--backend-token-env", default="SMART_LOGISTICS_API_TOKEN",
                        help="保存业务API Bearer Token的环境变量名")
    parser.add_argument("--amap-key-env", default="AMAP_WEB_SERVICE_KEY",
                        help="地址缺少经纬度时用于高德地理编码的环境变量名")
    parser.add_argument("--city", help="高德地址解析城市，例如重庆")
    parser.add_argument("--host", default="localhost", help="MQTT Broker地址")
    parser.add_argument("--port", type=int, default=1883, help="MQTT Broker端口")
    parser.add_argument("--credentials", type=pathlib.Path,
                        help="MQTT凭据env文件")
    parser.add_argument("--username", help="MQTT用户名")
    parser.add_argument("--password", help="MQTT密码（推荐改用--credentials）")
    parser.add_argument("--prefix", default="iot/carla", help="MQTT主题前缀")
    parser.add_argument("--qos", type=int, choices=(0, 1, 2), default=1)
    parser.add_argument("--timeout", type=float, default=30, help="等待ACK秒数")
    args = parser.parse_args()

    credential_values = {}
    if args.credentials:
        credentials = load_mqtt_credentials(args.credentials)
        credential_values = credentials
        if args.host == "localhost":
            args.host = credentials["MQTT_HOST"]
        if args.port == 1883:
            args.port = int(credentials["MQTT_EXTERNAL_PORT"])
        args.username = args.username or credentials["MQTT_USERNAME"]
        args.password = args.password or credentials["MQTT_PASSWORD"]

    if args.timeout <= 0:
        parser.error("--timeout必须大于0")
    if args.task_id is not None and args.task_id <= 0:
        parser.error("--task-id必须大于0")
    if args.task_url:
        amap_key = (
            os.environ.get(args.amap_key_env)
            or credential_values.get("AMAP_WEB_SERVICE_KEY")
        )
        try:
            context = fetch_task_route_context(
                args.task_url,
                token=os.environ.get(args.backend_token_env),
                amap_key=amap_key,
                city=args.city,
            )
        except Exception as exc:
            parser.error(str(exc))
        if args.task_id is not None and args.task_id != context["task_id"]:
            parser.error("--task-id与任务接口返回的任务ID不一致")
        args.task_id = context["task_id"]
        args.vehicle = args.vehicle or context["vehicle_id"]
        waypoints = context["waypoints"]
        print(
            f"[TASK] task_id={args.task_id}，"
            f"起点={context['start_name'] or '未命名'}，终点={context['end_name'] or '未命名'}"
        )
    else:
        args.vehicle = args.vehicle or "sim_000"
        waypoint_text = args.waypoints or (
            "106.750000,29.613500;106.770000,29.614500;106.790000,29.615000"
        )
        try:
            waypoints = parse_waypoints(waypoint_text)
        except ValueError as exc:
            parser.error(str(exc))
    if not args.vehicle:
        parser.error("任务接口必须返回vehicleDeviceCode，或显式传入--vehicle")

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
    if args.username:
        client.username_pw_set(args.username, args.password)

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
        print(f"[COMMAND_SENT] Topic={command_topic}")
        print(json.dumps(command, ensure_ascii=False, indent=2))
        if not ack_received.wait(timeout=args.timeout):
            raise TimeoutError(f"{args.timeout:g}秒内未收到ACK：{ack_topic}")
        print(f"[ACK_RECEIVED] Topic={ack_topic}")
        print(json.dumps(ack_holder, ensure_ascii=False, indent=2))
        if ack_holder.get("status") != "EXECUTED":
            raise RuntimeError("车辆返回执行失败")
    finally:
        client.disconnect()
        client.loop_stop()


if __name__ == "__main__":
    main()
