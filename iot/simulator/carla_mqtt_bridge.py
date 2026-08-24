# -*- coding: utf-8 -*-
"""
CARLA → MQTT 桥接脚本（智慧物流虚拟车队模拟器）
=================================================
作用：在 CARLA 城市地图中生成一支"运输车队"，每辆车开启自动驾驶，
     定时读取 GPS 位置 / 速度 / 航向，通过 MQTT 发布给后端，
     替代真实硬件（GPS 模块 + 鸿蒙开发板）完成端-云链路联调。

链路：CARLA 服务器（虚拟车队）→ 本脚本（桥）→ MQTT Broker → 后端 → 前端地图

用法：
    # 前置：先启动 CarlaUE4.exe（D:\CARLA\WindowsNoEditor\CarlaUE4.exe），
    #       再执行下方命令（另开一个终端跑脚本）
    python carla_mqtt_bridge.py                         # 默认 Town10HD_Opt, 5 辆车
    python carla_mqtt_bridge.py --vehicles 8            # 车数
    python carla_mqtt_bridge.py --map Town10HD          # 换地图
    python carla_mqtt_bridge.py --map current --gps-org 0,0  # 保留外部导入的 OSM/OpenDRIVE 地图
    python carla_mqtt_bridge.py --mqtt-host 192.168.1.10   # 指向自己的 MQTT Broker
    python carla_mqtt_bridge.py --demo-stops --demo-drift  # 注入异常：停留/偏航，触发告警
    python carla_mqtt_bridge.py --demo-stops --alert-mode raw  # 只制造异常，由后端判断告警
    python carla_mqtt_bridge.py --follow-vehicle 2         # 观察视角跟随 carla_02；传 -1 关闭
    python carla_mqtt_bridge.py --gps-org 0,0           # 用地图原始坐标（默认已平移到重庆 106.55,29.56）

订阅/发布主题（默认前缀 iot/carla，可改）：
    iot/carla/vehicle/{vehicle_id}/gps    车辆实时位置（发布，1Hz）
    iot/carla/vehicle/{vehicle_id}/status 车辆状态（发布）
    iot/carla/alert                      异常告警（发布）
"""
import argparse
import datetime
import json
import random
import sys
import threading
import time

try:
    import msvcrt  # Windows 控制台无阻塞读取按键
except ImportError:
    msvcrt = None

import carla
import paho.mqtt.client as mqtt

from tools.mqtt_credentials import load_mqtt_credentials

# ============================ 全局配置 ============================
# 注意：Town03 在部分显卡驱动上会崩溃（RTX 4060 Laptop 已实测），
# 默认用 Town10HD_Opt（自带默认地图，稳定）。想换地图请先单独测试。
DEFAULT_MAP = "Town10HD_Opt"    # 自带默认大城区地图
TOPIC_PREFIX = "iot/carla"      # MQTT 主题前缀
PUBLISH_INTERVAL = 1.0          # 位置发布间隔（秒）
DRIVER_MODELS = [               # 优先选外观像物流车的车型
    "vehicle.carlamotors.carlacola",
    "vehicle.carlamotors.firetruck",
    "vehicle.mercedes.coupe",
    "vehicle.audi.tt",
    "vehicle.tesla.model3",
    "vehicle.toyota.prius",
]

# ============================ 工具函数 ============================
def now_iso():
    """当前时间（ISO 8601，UTC），与 JSON 序列化兼容"""
    now = datetime.datetime.now(datetime.timezone.utc)
    return now.isoformat(timespec="milliseconds").replace("+00:00", "Z")


def gps_to_city(geo, dlat, dlon):
    """把 CARLA 默认的巴塞罗那坐标平移到目标城市附近（模拟"国内城市"GPS）"""
    return geo.latitude + dlat, geo.longitude + dlon


def build_vehicle_blueprint(world, rng):
    """挑选一个外观最像货车的车辆模型"""
    library = world.get_blueprint_library()
    models = [b for m in DRIVER_MODELS for b in library.filter(m)]
    if not models:
        models = library.filter("vehicle.*")
    bp = rng.choice(models)
    # 换一个醒目颜色，方便在地图上区分
    color = rng.choice(["255, 200, 0", "255, 110, 0", "0, 128, 255", "60, 180, 75", "200, 60, 60"])
    if bp.has_attribute("color"):
        bp.set_attribute("color", color)
    return bp


# ============================ 主流程 ============================
def main():
    parser = argparse.ArgumentParser(description="CARLA 虚拟车队 → MQTT 桥接")
    parser.add_argument("--host", default="localhost", help="CARLA 服务器地址")
    parser.add_argument("--port", type=int, default=2000, help="CARLA 端口")
    parser.add_argument("--map", default=DEFAULT_MAP,
                        help="地图名（Town01~Town15）；传 current 保留当前已加载地图")
    parser.add_argument("--vehicles", type=int, default=5, help="车队车辆数")
    parser.add_argument("--mqtt-host", default="localhost", help="MQTT Broker 地址")
    parser.add_argument("--mqtt-port", type=int, default=1883, help="MQTT Broker 端口")
    parser.add_argument("--mqtt-credentials",
                        help="MQTT 凭据 env 文件；会自动读取云端地址、端口和账号密码")
    parser.add_argument("--mqtt-username", help="MQTT 用户名")
    parser.add_argument("--mqtt-password", help="MQTT 密码（推荐改用 --mqtt-credentials）")
    parser.add_argument("--mqtt-prefix", default=TOPIC_PREFIX, help="MQTT 主题前缀")
    parser.add_argument("--mqtt-qos", type=int, choices=(0, 1, 2), default=1,
                        help="MQTT 发布 QoS，默认 1")
    parser.add_argument("--gps-org", default="106.55,29.56",
                        help="GPS 原点偏移（经度,纬度），默认平移到重庆；传 0,0 用地图原始坐标")
    parser.add_argument("--demo-stops", action="store_true",
                        help="演示模式：随机车辆异常停留（触发'异常停留'告警）")
    parser.add_argument("--demo-drift", action="store_true",
                        help="演示模式：随机车辆偏离路线（触发'偏航'告警）")
    parser.add_argument("--alert-mode", choices=("precomputed", "raw"), default="precomputed",
                        help="precomputed=模拟器同时发告警；raw=只制造异常，由后端检测")
    parser.add_argument("--follow-vehicle", type=int, default=0,
                        help="CARLA 观察视角跟随的车辆编号，默认 0；传 -1 关闭跟随")
    parser.add_argument("--fleet-overview", action="store_true",
                        help="启动后使用动态车队俯视总览，适合车辆较多时监管")
    parser.add_argument("--no-auto-follow-alert", action="store_true",
                        help="出现演示异常时不自动把视角切到异常车辆")
    args = parser.parse_args()

    if args.mqtt_credentials:
        credentials = load_mqtt_credentials(args.mqtt_credentials)
        if args.mqtt_host == "localhost":
            args.mqtt_host = credentials["MQTT_HOST"]
        if args.mqtt_port == 1883:
            args.mqtt_port = int(credentials["MQTT_EXTERNAL_PORT"])
        args.mqtt_username = args.mqtt_username or credentials["MQTT_USERNAME"]
        args.mqtt_password = args.mqtt_password or credentials["MQTT_PASSWORD"]

    # ---- 解析 GPS 偏移 ----
    dlat = dlon = 0.0
    if args.gps_org:
        try:
            dlon, dlat = map(float, args.gps_org.split(","))
        except ValueError:
            print("[配置] gps_org 格式应为 经度,纬度，忽略偏移")
    rng = random.Random(42)  # 固定随机种子，重启后车流一致，便于演示

    # ---- 连接 CARLA 服务器 ----
    print(f"[CARLA] 连接 {args.host}:{args.port} ...")
    client = carla.Client(args.host, args.port)
    client.set_timeout(60.0)
    try:
        world = client.get_world()
    except RuntimeError as exc:
        print(
            f"[CARLA] 连接失败：{exc}\n"
            "      这不是 MQTT 超时，而是 CARLA 服务端尚未监听 2000 端口。\n"
            "      请先在另一个 PowerShell 启动：\n"
            "      & 'D:\\CARLA\\WindowsNoEditor\\CarlaUE4.exe' -dx11 -quality-level=Low\n"
            "      等待城市画面完全出现后，再重新运行本脚本。"
        )
        sys.exit(1)
    current_map = world.get_map().name.rsplit("/", 1)[-1]
    keep_current_map = args.map.lower() == "current"
    requested_map = current_map if keep_current_map else args.map.rsplit("/", 1)[-1]
    if keep_current_map:
        print(f"[CARLA] 保留当前已加载地图：{current_map}")
    elif current_map != requested_map:
        # 注意：运行时切地图在部分 Windows 显卡驱动上可能崩溃，
        # 更稳妥的做法是启动服务器时直接带地图参数：
        #   CarlaUE4.exe /Game/Carla/Maps/Town03 -dx11 -quality-level=Low
        print(f"[CARLA] 运行时切换地图到 {args.map}（若服务器崩溃，请按上面注释的启动方式）...")
        world = client.load_world(requested_map)
        time.sleep(3)  # 等地图加载
    map_gps = world.get_map().transform_to_geolocation
    print(f"[CARLA] 地图加载完成: {world.get_map().name}")

    # ---- 清场：删除上次运行残留的车辆，保证可重复启动 ----
    for actor in world.get_actors().filter("vehicle.*"):
        actor.destroy()
    print("[CARLA] 已清理旧车辆")

    # ---- 生成车队 + 开启自动驾驶（走交通管制的随机路线） ----
    traffic_manager = client.get_trafficmanager(8000)
    traffic_manager.set_synchronous_mode(False)
    spawn_points = list(world.get_map().get_spawn_points())
    rng.shuffle(spawn_points)
    vehicles = []
    for point in spawn_points:
        if len(vehicles) >= args.vehicles:
            break
        i = len(vehicles)
        bp = build_vehicle_blueprint(world, rng)
        vehicle = world.try_spawn_actor(bp, point)
        if vehicle is None:
            print(f"[CARLA] 车辆 {i} 生成失败（点位被占用），跳过")
            continue
        vehicle.set_autopilot(True, 8000)
        vehicles.append((vehicle, i))
        print(f"[CARLA] 车辆 {i:02d} 已生成: {bp.id}")
    if not vehicles:
        print("[CARLA] 没有车辆生成成功，退出")
        sys.exit(1)

    # ---- CARLA 观察视角：默认跟随 carla_00，可在控制台一键切车 ----
    vehicle_by_id = {vid: vehicle for vehicle, vid in vehicles}
    vehicle_ids = sorted(vehicle_by_id)
    follow_vehicle = None
    follow_vid = None
    camera_mode = "manual"

    def select_follow_vehicle(vid):
        nonlocal follow_vehicle, follow_vid, camera_mode
        selected = vehicle_by_id.get(vid)
        if selected is None:
            return False
        follow_vehicle = selected
        follow_vid = vid
        camera_mode = "follow"
        print(f"[视角] 已切换到 carla_{vid:02d}")
        return True

    def select_fleet_overview():
        nonlocal camera_mode
        camera_mode = "overview"
        print("[视角] 已切换到车队俯视总览；出现异常时会自动跟随异常车辆")

    if args.fleet_overview:
        select_fleet_overview()
    elif args.follow_vehicle >= 0:
        if not select_follow_vehicle(args.follow_vehicle):
            fallback_vid = vehicle_ids[0]
            print(
                f"[视角] 未找到 carla_{args.follow_vehicle:02d}，"
                f"改为跟随 carla_{fallback_vid:02d}"
            )
            select_follow_vehicle(fallback_vid)

    spectator = world.get_spectator()

    def update_camera(snapshot=None):
        # 优先复用 world.on_tick 传入的快照，避免为了相机额外请求 CARLA 服务端。
        if snapshot is None:
            snapshot = world.get_snapshot()

        if camera_mode == "overview":
            locations = []
            for vehicle, _ in vehicles:
                actor_snapshot = snapshot.find(vehicle.id)
                if actor_snapshot is not None:
                    locations.append(actor_snapshot.get_transform().location)
            if not locations:
                return

            center_x = sum(location.x for location in locations) / len(locations)
            center_y = sum(location.y for location in locations) / len(locations)
            x_span = max(location.x for location in locations) - min(location.x for location in locations)
            y_span = max(location.y for location in locations) - min(location.y for location in locations)
            camera_height = max(60.0, min(400.0, max(x_span, y_span) * 0.8 + 40.0))
            spectator.set_transform(carla.Transform(
                carla.Location(x=center_x, y=center_y, z=camera_height),
                carla.Rotation(pitch=-90.0, yaw=0.0, roll=0.0),
            ))
            return

        if camera_mode != "follow":
            return
        if follow_vehicle is None or not follow_vehicle.is_alive:
            return
        actor_snapshot = snapshot.find(follow_vehicle.id)
        if actor_snapshot is None:
            return
        transform = actor_snapshot.get_transform()
        forward = transform.get_forward_vector()
        camera_location = carla.Location(
            x=transform.location.x - forward.x * 8.0,
            y=transform.location.y - forward.y * 8.0,
            z=transform.location.z + 4.0,
        )
        camera_rotation = carla.Rotation(
            pitch=-15.0,
            yaw=transform.rotation.yaw,
            roll=0.0,
        )
        spectator.set_transform(carla.Transform(camera_location, camera_rotation))

    def switch_relative(step):
        if not vehicle_ids:
            return
        if follow_vid not in vehicle_ids:
            select_follow_vehicle(vehicle_ids[0])
            return
        current_index = vehicle_ids.index(follow_vid)
        next_index = (current_index + step) % len(vehicle_ids)
        select_follow_vehicle(vehicle_ids[next_index])

    def handle_camera_hotkeys():
        if msvcrt is None:
            return
        while msvcrt.kbhit():
            key = msvcrt.getwch().lower()
            if key == "\x03":
                raise KeyboardInterrupt
            if key == "n":
                switch_relative(1)
            elif key == "p":
                switch_relative(-1)
            elif key == "o":
                select_fleet_overview()

    update_camera()

    # ---- 连接 MQTT Broker ----
    connected = threading.Event()
    mqtt_client = mqtt.Client(
        mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"carla-bridge-{int(time.time())}",
    )
    if args.mqtt_username:
        mqtt_client.username_pw_set(args.mqtt_username, args.mqtt_password)

    def on_connect(client_obj, userdata, flags, reason_code, properties):
        if reason_code == 0:
            connected.set()
        else:
            print(f"[MQTT] Broker 拒绝连接：{reason_code}")

    mqtt_client.on_connect = on_connect
    try:
        mqtt_client.connect(args.mqtt_host, args.mqtt_port, keepalive=30)
        mqtt_client.loop_start()
        if not connected.wait(timeout=5.0):
            raise TimeoutError("等待 MQTT CONNACK 超时")
        print(f"[MQTT] 已连接 {args.mqtt_host}:{args.mqtt_port}")
    except Exception as exc:
        print(f"[MQTT] 连接失败（{exc}）\n      请确认 Broker 已启动，或先用 --mqtt-host 指定地址")
        sys.exit(1)

    def publish(topic, payload, retain=False):
        info = mqtt_client.publish(
            f"{args.mqtt_prefix}/{topic}",
            json.dumps(payload, ensure_ascii=False),
            qos=args.mqtt_qos,
            retain=retain,
        )
        if info.rc != mqtt.MQTT_ERR_SUCCESS:
            print(f"[MQTT] 发布失败：topic={topic}, rc={info.rc}")
        return info

    def publish_status(vid, online):
        vid_name = f"carla_{vid:02d}"
        return publish(f"vehicle/{vid_name}/status", {
            "schema_version": "1.0",
            "vehicle_id": vid_name,
            "timestamp": now_iso(),
            "online": online,
            "transport_status": "运输中",
        }, retain=True)

    for _, vid in vehicles:
        publish_status(vid, True)

    # ---- 演示模式状态 ----
    stop_until = {}     # vid -> 恢复时间戳（异常停留）
    drift_until = {}    # vid -> 恢复时间戳（偏航）
    last_alert = {}     # 去重：同一辆车同一告警 10 秒内不重复发
    demo_schedule = {
        "next_stop": time.time() + 5.0,
        "next_drift": time.time() + 10.0,
    }

    def send_alert(vehicle_id, kind, desc):
        key = (vehicle_id, kind)
        now = time.time()
        if now - last_alert.get(key, 0) < 10:
            return
        last_alert[key] = now
        publish("alert", {
            "schema_version": "1.0",
            "vehicle_id": vehicle_id,
            "alert_type": kind,
            "description": desc,
            "timestamp": now_iso(),
            "source": "simulator",
        })
        print(f"  [告警] {vehicle_id}: {desc}")

    def demo_tick(now):
        """演示模式：每个周期只选择一辆空闲车辆注入异常。"""
        by_vid = {vid: vehicle for vehicle, vid in vehicles}

        for vid, recover_at in list(stop_until.items()):
            if now >= recover_at:
                traffic_manager.vehicle_percentage_speed_difference(by_vid[vid], 0.0)
                del stop_until[vid]

        for vid, recover_at in list(drift_until.items()):
            if now >= recover_at:
                by_vid[vid].set_autopilot(True, 8000)
                del drift_until[vid]

        if args.demo_stops and now >= demo_schedule["next_stop"]:
            candidates = [(vehicle, vid) for vehicle, vid in vehicles
                          if vid not in stop_until and vid not in drift_until]
            demo_schedule["next_stop"] = now + 20.0
            if candidates:
                vehicle, vid = rng.choice(candidates)
                vehicle_id = f"carla_{vid:02d}"
                duration = 8.0
                stop_until[vid] = now + duration
                traffic_manager.vehicle_percentage_speed_difference(vehicle, 100.0)
                print(f"  [异常注入] {vehicle_id}: 停留 {duration:.0f} 秒")
                if not args.no_auto_follow_alert:
                    print(f"[视角] 检测到异常停留，自动跟随 {vehicle_id}")
                    select_follow_vehicle(vid)
                if args.alert_mode == "precomputed":
                    send_alert(vehicle_id, "异常停留", f"车辆异常停留 {duration:.0f} 秒（演示）")

        if args.demo_drift and now >= demo_schedule["next_drift"]:
            candidates = [(vehicle, vid) for vehicle, vid in vehicles
                          if vid not in stop_until and vid not in drift_until]
            demo_schedule["next_drift"] = now + 30.0
            if candidates:
                vehicle, vid = rng.choice(candidates)
                vehicle_id = f"carla_{vid:02d}"
                duration = 5.0
                drift_until[vid] = now + duration
                vehicle.set_autopilot(False)
                vehicle.apply_control(carla.VehicleControl(throttle=0.9, steer=0.8, brake=0.0))
                print(f"  [异常注入] {vehicle_id}: 偏航 {duration:.0f} 秒")
                if not args.no_auto_follow_alert:
                    print(f"[视角] 检测到偏航，自动跟随 {vehicle_id}")
                    select_follow_vehicle(vid)
                if args.alert_mode == "precomputed":
                    send_alert(vehicle_id, "偏航", "车辆偏离规划路线（演示）")

    def on_tick(snapshot):
        """CARLA 服务器每帧回调（约 20Hz），节流到 1Hz 发布"""
        # 相机与模拟帧同步更新，避免主线程轮询造成视角跳动和额外 RPC。
        update_camera(snapshot)
        now = time.time()
        demo_tick(now)
        if now - getattr(on_tick, "last", 0) < PUBLISH_INTERVAL:
            return
        published = 0
        for vehicle, vid in vehicles:
            v = vehicle.get_velocity()
            speed = 3.6 * (v.x ** 2 + v.y ** 2 + v.z ** 2) ** 0.5  # m/s -> km/h
            transform = vehicle.get_transform()
            lat, lon = gps_to_city(map_gps(transform.location), dlat, dlon)
            vid_name = f"carla_{vid:02d}"
            payload = {
                "schema_version": "1.0",
                "vehicle_id": vid_name,
                "timestamp": now_iso(),
                "lat": round(lat, 6),
                "lon": round(lon, 6),
                "speed_kmh": round(speed, 1),
                "heading": round(transform.rotation.yaw % 360.0, 1),
                "transport_status": "运输中",   # 待装货 / 已装货 / 运输中 / 已送达
                "coordinate_system": "WGS84",
            }
            publish(f"vehicle/{vid_name}/gps", payload)
            published += 1
        if published:
            on_tick.last = now
            print(f"[发布] {now_iso()} 已发布 {published} 辆车位置")

    on_tick.last = 0.0
    tick_callback_id = world.on_tick(on_tick)
    print(f"=== 车队模拟器运行中：{len(vehicles)} 辆车，地图 {args.map}，"
          f"MQTT {args.mqtt_host}:{args.mqtt_port}，主题前缀 {args.mqtt_prefix} ===")
    print("视角快捷键（先点一下本 PowerShell 窗口）：O=车队俯视总览，N=下一辆，P=上一辆")
    if not args.no_auto_follow_alert:
        print("异常监管：发现异常停留或偏航时，视角会自动切换到异常车辆")
    print("按 Ctrl+C 退出（退出时自动销毁车辆）")

    try:
        while True:
            # 相机由 world.on_tick 随模拟帧更新；主线程只处理控制台快捷键。
            handle_camera_hotkeys()
            time.sleep(0.05)
    except KeyboardInterrupt:
        pass
    finally:
        print("\n[退出] 清理中...")
        world.remove_on_tick(tick_callback_id)
        status_infos = [publish_status(vid, False) for _, vid in vehicles]
        for info in status_infos:
            try:
                info.wait_for_publish(timeout=2.0)
            except RuntimeError:
                pass
        for vehicle, _ in vehicles:
            vehicle.destroy()
        mqtt_client.loop_stop()
        mqtt_client.disconnect()
        print("[退出] 完成")


if __name__ == "__main__":
    main()
