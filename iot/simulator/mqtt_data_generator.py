# -*- coding: utf-8 -*-
"""智慧物流批量随机车队数据发生器。

不依赖 CARLA，可生成任意数量车辆的连续 GPS 流，直接发布到 MQTT，
并可将完全相同的数据保存成兼容 mqtt_replay.py 的 JSONL 文件。
"""
import argparse
import datetime
import json
import math
import os
import pathlib
import queue
import random
import threading
import time

import paho.mqtt.client as mqtt

from mqtt_credentials import load_mqtt_credentials
from task_route import fetch_task_route


EARTH_RADIUS_M = 6_371_000.0


def now_iso():
    now = datetime.datetime.now(datetime.timezone.utc)
    return now.isoformat(timespec="milliseconds").replace("+00:00", "Z")


def move_point(lat, lon, distance_m, heading_deg):
    """按距离和航向移动 WGS84 点，适用于本项目短距离模拟。"""
    angular_distance = distance_m / EARTH_RADIUS_M
    heading = math.radians(heading_deg)
    lat1 = math.radians(lat)
    lon1 = math.radians(lon)
    lat2 = math.asin(
        math.sin(lat1) * math.cos(angular_distance)
        + math.cos(lat1) * math.sin(angular_distance) * math.cos(heading)
    )
    lon2 = lon1 + math.atan2(
        math.sin(heading) * math.sin(angular_distance) * math.cos(lat1),
        math.cos(angular_distance) - math.sin(lat1) * math.sin(lat2),
    )
    return math.degrees(lat2), math.degrees(lon2)


def distance_between(lat1, lon1, lat2, lon2):
    """计算两个短距离 WGS84 点之间的米数。"""
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (
        math.sin(dlat / 2) ** 2
        + math.cos(math.radians(lat1))
        * math.cos(math.radians(lat2))
        * math.sin(dlon / 2) ** 2
    )
    return EARTH_RADIUS_M * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def bearing_between(lat1, lon1, lat2, lon2):
    """计算起点朝向终点的方位角。"""
    lat1_rad = math.radians(lat1)
    lat2_rad = math.radians(lat2)
    dlon = math.radians(lon2 - lon1)
    y = math.sin(dlon) * math.cos(lat2_rad)
    x = (
        math.cos(lat1_rad) * math.sin(lat2_rad)
        - math.sin(lat1_rad) * math.cos(lat2_rad) * math.cos(dlon)
    )
    return math.degrees(math.atan2(y, x)) % 360.0


def advance_on_route(vehicle, route_points, distance_m):
    """沿业务后端保存的单程路线前进，到达终点后保持静止。"""
    while distance_m > 0 and not vehicle.get("route_complete"):
        next_index = vehicle["route_next_index"]
        if next_index >= len(route_points):
            vehicle["route_complete"] = True
            vehicle["speed_kmh"] = 0.0
            vehicle["transport_status"] = "已送达"
            return
        target_lat, target_lon = route_points[next_index]
        segment_m = distance_between(
            vehicle["lat"], vehicle["lon"], target_lat, target_lon
        )
        if segment_m < 0.05:
            vehicle["route_next_index"] = next_index + 1
            continue

        vehicle["heading"] = bearing_between(
            vehicle["lat"], vehicle["lon"], target_lat, target_lon
        )
        if distance_m >= segment_m:
            vehicle["lat"], vehicle["lon"] = target_lat, target_lon
            vehicle["route_next_index"] = next_index + 1
            distance_m -= segment_m
        else:
            ratio = distance_m / segment_m
            vehicle["lat"] += (target_lat - vehicle["lat"]) * ratio
            vehicle["lon"] += (target_lon - vehicle["lon"]) * ratio
            distance_m = 0


def make_vehicle(index, vehicle_count, origin_lat, origin_lon, rng):
    lat, lon = move_point(origin_lat, origin_lon, rng.uniform(0, 2500), rng.uniform(0, 360))
    heading = rng.uniform(0, 360)

    vehicle = {
        "vehicle_id": f"sim_{index:03d}",
        "lat": lat,
        "lon": lon,
        "speed_kmh": rng.uniform(20, 55),
        "heading": heading,
        "transport_status": rng.choice(["已装货", "运输中", "运输中", "运输中"]),
        "anomaly": None,
        "anomaly_ticks": 0,
        "route_points": None,
        "route_complete": False,
        "active_task_id": None,
        "route_id": None,
        "route_version": 0,
    }
    return vehicle


def install_task_route(vehicle, route):
    """Install a READY route unless the vehicle already has this or a newer version."""
    current_route_id = vehicle.get("route_id")
    current_version = int(vehicle.get("route_version") or 0)
    if current_route_id == route["route_id"] and current_version >= route["route_version"]:
        return False

    points = route["points"]
    same_task = vehicle.get("active_task_id") == route["task_id"]
    if same_task and vehicle.get("route_points"):
        nearest_index = min(
            range(len(points)),
            key=lambda index: distance_between(
                vehicle["lat"], vehicle["lon"], points[index][0], points[index][1]
            ),
        )
        vehicle["route_next_index"] = min(nearest_index + 1, len(points))
    else:
        vehicle["lat"], vehicle["lon"] = points[0]
        vehicle["route_next_index"] = 1

    vehicle["route_points"] = points
    vehicle["route_complete"] = False
    vehicle["active_task_id"] = route["task_id"]
    vehicle["route_id"] = route["route_id"]
    vehicle["route_version"] = route["route_version"]
    vehicle["transport_status"] = "运输中"
    vehicle["speed_kmh"] = max(20.0, vehicle["speed_kmh"])
    if vehicle["route_next_index"] < len(points):
        vehicle["heading"] = bearing_between(
            vehicle["lat"], vehicle["lon"], *points[vehicle["route_next_index"]]
        )
    return True


def main():
    parser = argparse.ArgumentParser(description="批量生成并发布随机车辆 MQTT 数据")
    parser.add_argument("--vehicles", type=int, default=20, help="车辆数，默认 20")
    parser.add_argument("--duration", type=float, default=60,
                        help="运行秒数；0 表示直到 Ctrl+C")
    parser.add_argument("--interval", type=float, default=1.0,
                        help="每批 GPS 发布间隔秒数，默认 1")
    parser.add_argument("--origin", default="106.55,29.56",
                        help="中心点，经度,纬度，默认重庆")
    parser.add_argument("--task-id", action="append", type=int, default=[],
                        help="启动时加载的运输任务ID；可重复指定")
    parser.add_argument("--business-api-base",
                        help="业务后端地址，例如http://server:8080或其/api/v1地址")
    parser.add_argument("--business-token-env", default="SMART_LOGISTICS_API_TOKEN",
                        help="保存业务API Bearer Token的环境变量名")
    parser.add_argument("--seed", type=int, default=42,
                        help="随机种子；相同种子可复现相同车流")
    parser.add_argument("--anomaly-rate", type=float, default=0.0,
                        help="每车每批触发异常的概率，建议 0~0.02")
    parser.add_argument(
        "--demo-anomaly",
        choices=("stop", "drift", "open"),
        help="启动后在 sim_000 上确定性注入一次异常，便于演示和验收",
    )
    parser.add_argument("--alert-mode", choices=("precomputed", "raw"),
                        default="precomputed")
    parser.add_argument("--host", default="localhost", help="MQTT Broker 地址")
    parser.add_argument("--port", type=int, default=1883, help="MQTT Broker 端口")
    parser.add_argument("--credentials", type=pathlib.Path,
                        help="MQTT 凭据 env 文件；会自动读取云端地址、端口和账号密码")
    parser.add_argument("--username", help="MQTT 用户名")
    parser.add_argument("--password", help="MQTT 密码（推荐改用 --credentials）")
    parser.add_argument("--prefix", default="iot/carla", help="MQTT 主题前缀")
    parser.add_argument("--qos", type=int, choices=(0, 1, 2), default=1)
    parser.add_argument("--output", help="可选：同时保存成可回放 JSONL")
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

    if args.vehicles <= 0:
        parser.error("--vehicles 必须大于 0")
    if args.duration < 0:
        parser.error("--duration 不能小于 0")
    if args.interval <= 0:
        parser.error("--interval 必须大于 0")
    if not 0 <= args.anomaly_rate <= 1:
        parser.error("--anomaly-rate 必须在 0 到 1 之间")
    if args.demo_anomaly == "open" and args.alert_mode == "raw":
        parser.error("异常开箱没有 GPS 原始特征，请使用 --alert-mode precomputed")
    try:
        origin_lon, origin_lat = (float(item) for item in args.origin.split(","))
    except ValueError:
        parser.error("--origin 格式应为 经度,纬度")

    if any(task_id <= 0 for task_id in args.task_id):
        parser.error("--task-id必须大于0")
    business_api_base = (
        args.business_api_base
        or os.environ.get("SMART_LOGISTICS_API_BASE_URL")
        or credential_values.get("BUSINESS_API_BASE_URL")
    )
    business_api_token = (
        os.environ.get(args.business_token_env)
        or credential_values.get("SMART_LOGISTICS_API_TOKEN")
    )
    if args.task_id and not business_api_base:
        parser.error("使用--task-id时必须配置--business-api-base")

    rng = random.Random(args.seed)
    vehicles = [
        make_vehicle(
            i,
            args.vehicles,
            origin_lat,
            origin_lon,
            rng,
        )
        for i in range(args.vehicles)
    ]
    vehicles_by_id = {vehicle["vehicle_id"]: vehicle for vehicle in vehicles}
    for task_id in args.task_id:
        try:
            initial_route = fetch_task_route(
                business_api_base, task_id, token=business_api_token
            )
        except Exception as exc:
            parser.error(f"任务{task_id}路线加载失败：{exc}")
        initial_vehicle = vehicles_by_id.get(initial_route["vehicle_id"])
        if initial_vehicle is None:
            parser.error(
                f"任务{task_id}绑定车辆{initial_route['vehicle_id']}不在本次模拟车队中"
            )
        install_task_route(initial_vehicle, initial_route)
        print(
            f"[ROUTE][LOADED] task_id={task_id}，"
            f"vehicle={initial_route['vehicle_id']}，"
            f"generated_at={initial_route['generated_at']}，"
            f"points={len(initial_route['points'])}"
        )
    connected = threading.Event()
    ever_connected = threading.Event()
    online_status_pending = threading.Event()
    shutting_down = threading.Event()
    command_queue = queue.Queue()
    route_result_queue = queue.Queue()
    command_topic = f"{args.prefix}/vehicle/+/command"
    client = mqtt.Client(
        mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"smart-logistics-generator-{int(time.time())}",
    )
    if args.username:
        client.username_pw_set(args.username, args.password)
    client.reconnect_delay_set(min_delay=1, max_delay=30)

    def on_connect(client_obj, userdata, flags, reason_code, properties):
        if reason_code == 0:
            client_obj.subscribe(command_topic, qos=args.qos)
            connected.set()
            online_status_pending.set()
            if ever_connected.is_set():
                print("[MQTT][RECONNECTED] 已重新连接 Broker，将恢复车辆在线状态和 GPS 发布")
            else:
                print(f"[MQTT][CONNECTED] 已连接 {args.host}:{args.port}")
                ever_connected.set()
        else:
            connected.clear()
            print(f"[错误] Broker 拒绝连接：{reason_code}")

    def on_disconnect(client_obj, userdata, disconnect_flags, reason_code, properties):
        connected.clear()
        if not shutting_down.is_set():
            print(
                f"[MQTT][DISCONNECTED] 连接已断开：{reason_code}；暂停生成新批次，"
                "后台将按 1~30 秒退避自动重连"
            )

    def on_message(client_obj, userdata, message):
        # MQTT 网络线程只负责入队；路线加载和车辆状态更新由主循环执行。
        command_queue.put((message.topic, bytes(message.payload)))

    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.on_message = on_message
    client.connect(args.host, args.port, keepalive=30)
    client.loop_start()
    if not connected.wait(timeout=5):
        shutting_down.set()
        client.disconnect()
        client.loop_stop()
        raise TimeoutError("等待 MQTT CONNACK 超时")

    output_stream = None
    last_output_at = None
    if args.output:
        output_path = pathlib.Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_stream = output_path.open("w", encoding="utf-8", newline="\n")

    published = 0
    alert_count = 0
    command_count = 0
    command_failure_count = 0
    processed_commands = {}
    active_commands = set()

    def wait_for_connection():
        if connected.is_set():
            return True
        waiting_started = time.monotonic()
        last_notice_second = 0
        print("[MQTT][RECONNECTING] 等待连接恢复，车辆位置保持不变……")
        while not connected.wait(timeout=1):
            if shutting_down.is_set():
                return False
            waited = int(time.monotonic() - waiting_started)
            if waited >= 10 and waited // 10 > last_notice_second // 10:
                last_notice_second = waited
                print(f"[MQTT][RECONNECTING] 已等待 {waited} 秒，仍在自动重连……")
        waited = time.monotonic() - waiting_started
        print(f"[MQTT][RESUMED] 连接已恢复，等待 {waited:.1f} 秒后继续原轨迹")
        return True

    def publish(topic, payload, retain=False):
        nonlocal published, last_output_at
        full_topic = f"{args.prefix}/{topic}"
        encoded_payload = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
        while True:
            if not wait_for_connection():
                raise RuntimeError("程序正在关闭，停止等待 MQTT 重连")
            info = client.publish(
                full_topic,
                encoded_payload,
                qos=args.qos,
                retain=retain,
            )
            if info.rc == mqtt.MQTT_ERR_SUCCESS:
                break
            if info.rc == mqtt.MQTT_ERR_NO_CONN:
                connected.clear()
                print(f"[MQTT][RETRY] 发布时发现连接不可用，将重试同一消息：{full_topic}")
                continue
            raise RuntimeError(f"发布失败：topic={full_topic}, rc={info.rc}")
        published += 1
        if output_stream is not None:
            current = time.monotonic()
            delay_ms = 0 if last_output_at is None else round((current - last_output_at) * 1000)
            event = {
                "delay_ms": delay_ms,
                "topic": full_topic,
                "qos": args.qos,
                "retain": retain,
                "payload": payload,
            }
            output_stream.write(json.dumps(event, ensure_ascii=False, separators=(",", ":")) + "\n")
            output_stream.flush()
            last_output_at = current
        return info

    def publish_status(vehicle, online):
        return publish(f"vehicle/{vehicle['vehicle_id']}/status", {
            "schema_version": "1.0",
            "vehicle_id": vehicle["vehicle_id"],
            "timestamp": now_iso(),
            "online": online,
            "transport_status": vehicle["transport_status"],
        }, retain=True)

    def publish_alert(vehicle, alert_type, description):
        nonlocal alert_count
        if args.alert_mode == "raw":
            return
        publish("alert", {
            "schema_version": "1.0",
            "vehicle_id": vehicle["vehicle_id"],
            "alert_type": alert_type,
            "description": description,
            "timestamp": now_iso(),
            "source": "simulator",
        })
        alert_count += 1

    def start_anomaly(vehicle, anomaly_type):
        """在指定车辆上启动一个可观察、可复现的异常场景。"""
        vehicle["anomaly"] = anomaly_type
        if anomaly_type == "异常停留":
            vehicle["speed_kmh"] = 0.0
            vehicle["anomaly_ticks"] = max(2, round(8 / args.interval))
            publish_alert(vehicle, "异常停留", "车辆异常停留（批量模拟）")
        elif anomaly_type == "偏航":
            vehicle["heading"] = (vehicle["heading"] + rng.uniform(60, 120)) % 360
            vehicle["anomaly_ticks"] = max(2, round(5 / args.interval))
            publish_alert(vehicle, "偏航", "车辆偏离规划路线（批量模拟）")
        elif anomaly_type == "异常开箱":
            vehicle["anomaly_ticks"] = max(2, round(5 / args.interval))
            publish_alert(vehicle, "异常开箱", "运输途中检测到箱门开启（批量模拟）")
        else:
            raise ValueError(f"不支持的异常类型：{anomaly_type}")

    def publish_command_ack(command_id, vehicle_id, status, message, route_point_count=None):
        payload = {
            "schema_version": "1.0",
            "command_id": command_id,
            "vehicle_id": vehicle_id,
            "command_type": "TASK_ROUTE_READY",
            "status": status,
            "message": message,
            "timestamp": now_iso(),
        }
        if route_point_count is not None:
            payload["route_point_count"] = route_point_count
        publish(f"vehicle/{vehicle_id}/command/ack", payload)
        return payload

    def fail_command(command_id, vehicle_id, message):
        nonlocal command_failure_count
        command_failure_count += 1
        if command_id:
            active_commands.discard(command_id)
        print(f"[指令失败] command_id={command_id}，vehicle_id={vehicle_id}：{message}")
        if command_id and vehicle_id:
            ack = publish_command_ack(command_id, vehicle_id, "FAILED", message)
            processed_commands[command_id] = ack

    def process_command(topic, raw_payload):
        nonlocal command_count
        topic_parts = topic.split("/")
        topic_vehicle_id = topic_parts[-2] if len(topic_parts) >= 2 else ""
        try:
            command = json.loads(raw_payload.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            fail_command(None, topic_vehicle_id, f"JSON解析失败：{exc}")
            return

        if not isinstance(command, dict):
            fail_command(None, topic_vehicle_id, "Payload必须是JSON对象")
            return

        command_id = command.get("command_id")
        vehicle_id = command.get("vehicle_id")
        if not isinstance(command_id, str) or not command_id.strip():
            fail_command(None, topic_vehicle_id, "缺少有效的command_id")
            return
        command_id = command_id.strip()

        # QoS 1 或后端重试可能造成重复消息；原样重发首次 ACK，不重复切路线。
        if command_id in processed_commands:
            ack = processed_commands[command_id]
            publish(f"vehicle/{ack['vehicle_id']}/command/ack", ack)
            print(f"[重复指令] command_id={command_id}，已重发原ACK")
            return
        if command_id in active_commands:
            print(f"[重复指令] command_id={command_id}仍在规划中，等待首次ACK")
            return

        if not isinstance(vehicle_id, str) or not vehicle_id:
            fail_command(command_id, topic_vehicle_id, "缺少有效的vehicle_id")
            return
        if topic_vehicle_id != vehicle_id:
            fail_command(command_id, topic_vehicle_id, "Topic中的车辆编号与Payload.vehicle_id不一致")
            return
        if command.get("schema_version") != "1.0":
            fail_command(command_id, vehicle_id, "schema_version必须为1.0")
            return
        if command.get("command_type") != "TASK_ROUTE_READY":
            fail_command(command_id, vehicle_id, "当前仅支持TASK_ROUTE_READY")
            return
        vehicle = vehicles_by_id.get(vehicle_id)
        if vehicle is None:
            fail_command(command_id, vehicle_id, "模拟器中不存在该车辆")
            return

        task_id = command.get("task_id")
        if (
            isinstance(task_id, bool) or not isinstance(task_id, int) or task_id <= 0
        ):
            fail_command(command_id, vehicle_id, "task_id必须是正整数")
            return
        if not business_api_base:
            fail_command(command_id, vehicle_id, "未配置业务后端API地址")
            return

        active_commands.add(command_id)

        def load_route_in_background():
            try:
                route = fetch_task_route(
                    business_api_base,
                    task_id,
                    token=business_api_token,
                )
                route_result_queue.put((
                    command_id, vehicle_id, task_id, route, None,
                ))
            except Exception as exc:
                route_result_queue.put((
                    command_id, vehicle_id, task_id, None, str(exc),
                ))

        threading.Thread(
            target=load_route_in_background,
            name=f"task-route-{command_id}",
            daemon=True,
        ).start()
        print(f"[ROUTE][FETCHING] command_id={command_id}，task_id={task_id}")

    def apply_route_result(command_id, vehicle_id, task_id, route, error):
        nonlocal command_count
        if error:
            fail_command(command_id, vehicle_id, f"业务路线加载失败：{error}")
            return
        vehicle = vehicles_by_id.get(vehicle_id)
        if vehicle is None:
            fail_command(command_id, vehicle_id, "路线加载完成时车辆已不存在")
            return

        if route["task_id"] != task_id:
            fail_command(command_id, vehicle_id, "planned-route返回的taskId不一致")
            return
        if route["vehicle_id"] != vehicle_id:
            fail_command(command_id, vehicle_id, "planned-route返回的vehicleDeviceCode不一致")
            return

        installed = install_task_route(vehicle, route)
        vehicle["anomaly"] = None
        vehicle["anomaly_ticks"] = 0
        vehicle["active_command_id"] = command_id

        active_commands.discard(command_id)
        command_count += 1
        ack = publish_command_ack(
            command_id,
            vehicle_id,
            "EXECUTED",
            "ETA规划路线已加载，车辆开始沿points行驶"
            if installed else "该任务规划路线已加载，无需重复切换",
            route_point_count=len(route["points"]),
        )
        processed_commands[command_id] = ack
        if len(processed_commands) > 1000:
            processed_commands.pop(next(iter(processed_commands)))
        print(
            f"[ROUTE][APPLIED] command_id={command_id}，vehicle_id={vehicle_id}，"
            f"generated_at={route['generated_at']}，"
            f"points={len(route['points'])}"
        )

    def drain_commands():
        while True:
            try:
                topic, raw_payload = command_queue.get_nowait()
            except queue.Empty:
                break
            process_command(topic, raw_payload)
        while True:
            try:
                result = route_result_queue.get_nowait()
            except queue.Empty:
                return
            apply_route_result(*result)

    online_status_pending.clear()
    for vehicle in vehicles:
        publish_status(vehicle, True)

    started = time.monotonic()
    batch = 0
    print(
        f"[启动] {args.vehicles} 辆车，间隔 {args.interval}s，"
        f"Broker={args.host}:{args.port}，异常概率={args.anomaly_rate}"
    )
    print(f"[订阅] {command_topic}（接收TASK_ROUTE_READY任务路线刷新通知）")
    try:
        while args.duration == 0 or time.monotonic() - started < args.duration:
            if not wait_for_connection():
                break
            if online_status_pending.is_set():
                online_status_pending.clear()
                print("[MQTT][ONLINE_RESTORED] 正在恢复全部车辆在线状态……")
                for vehicle in vehicles:
                    publish_status(vehicle, True)
            batch_started = time.monotonic()
            batch += 1
            drain_commands()
            for vehicle in vehicles:
                if vehicle["anomaly_ticks"] > 0:
                    vehicle["anomaly_ticks"] -= 1
                    if vehicle["anomaly_ticks"] == 0:
                        vehicle["anomaly"] = None
                        vehicle["speed_kmh"] = rng.uniform(20, 45)
                elif batch == 1 and vehicle["vehicle_id"] == "sim_000" and args.demo_anomaly:
                    demo_types = {
                        "stop": "异常停留",
                        "drift": "偏航",
                        "open": "异常开箱",
                    }
                    start_anomaly(vehicle, demo_types[args.demo_anomaly])
                elif rng.random() < args.anomaly_rate:
                    anomaly_types = ["异常停留", "偏航"]
                    if args.alert_mode == "precomputed":
                        anomaly_types.append("异常开箱")
                    start_anomaly(vehicle, rng.choice(anomaly_types))

                if vehicle.get("route_complete"):
                    vehicle["speed_kmh"] = 0.0
                elif vehicle["anomaly"] != "异常停留":
                    vehicle["speed_kmh"] = min(70, max(5, vehicle["speed_kmh"] + rng.uniform(-2, 2)))
                    distance_m = vehicle["speed_kmh"] / 3.6 * args.interval
                    vehicle_route = vehicle.get("route_points")
                    if vehicle_route:
                        advance_on_route(vehicle, vehicle_route, distance_m)
                    else:
                        if vehicle["anomaly"] != "偏航":
                            vehicle["heading"] = (vehicle["heading"] + rng.uniform(-4, 4)) % 360
                        vehicle["lat"], vehicle["lon"] = move_point(
                            vehicle["lat"], vehicle["lon"], distance_m, vehicle["heading"]
                        )

                publish(f"vehicle/{vehicle['vehicle_id']}/gps", {
                    "schema_version": "1.0",
                    "vehicle_id": vehicle["vehicle_id"],
                    "timestamp": now_iso(),
                    "lat": round(vehicle["lat"], 6),
                    "lon": round(vehicle["lon"], 6),
                    "speed_kmh": round(vehicle["speed_kmh"], 1),
                    "heading": round(vehicle["heading"], 1),
                    "transport_status": vehicle["transport_status"],
                    "coordinate_system": "WGS84",
                })

            print(f"[批次 {batch}] 已发布 {args.vehicles} 条 GPS，累计告警 {alert_count}")
            remaining = args.interval - (time.monotonic() - batch_started)
            if remaining > 0:
                time.sleep(remaining)
    except KeyboardInterrupt:
        pass
    finally:
        shutting_down.set()
        offline_infos = []
        if connected.is_set():
            for vehicle in vehicles:
                try:
                    offline_infos.append(publish_status(vehicle, False))
                except RuntimeError:
                    break
        else:
            print("[MQTT][SHUTDOWN] 退出时连接不可用，跳过离线状态发布")
        for info in offline_infos:
            try:
                info.wait_for_publish(timeout=2)
            except RuntimeError:
                pass
        if output_stream is not None:
            output_stream.close()
        client.disconnect()
        client.loop_stop()

    elapsed = time.monotonic() - started
    print(
        f"[完成] 运行 {elapsed:.1f}s，共发布 {published} 条消息、{alert_count} 条告警，"
        f"执行指令 {command_count} 条、失败 {command_failure_count} 条"
    )
    if args.output:
        print(f"[文件] {pathlib.Path(args.output).resolve()}")


if __name__ == "__main__":
    main()
