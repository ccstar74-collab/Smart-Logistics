# -*- coding: utf-8 -*-
"""Start route-based MQTT GPS publishers only when live GPS is unavailable.

This is an operator-started fallback process.  It never creates tasks, changes task
status, or modifies vehicles.  While it is running it polls TRANSPORTING tasks and
starts one ``mqtt_data_generator.py`` child per eligible simulated vehicle.
"""

import argparse
import datetime as dt
import json
import os
import pathlib
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass

from task_route import parse_task_route


SIM_CODE_PATTERN = re.compile(r"^sim_(\d{3})$")
TERMINAL_STATUSES = {"COMPLETED", "CANCELLED"}


def unwrap_api_response(payload):
    if not isinstance(payload, dict):
        raise ValueError("业务后端响应必须是JSON对象")
    if payload.get("code") not in (None, 0, 200):
        raise RuntimeError(
            "业务后端返回失败：%s" % (payload.get("message") or payload.get("code"))
        )
    return payload.get("data") if "data" in payload else payload


def parse_api_time(value):
    if not isinstance(value, str) or not value.strip():
        return None
    normalized = value.strip()
    if normalized.endswith("Z"):
        normalized = normalized[:-1] + "+00:00"
    try:
        parsed = dt.datetime.fromisoformat(normalized)
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=dt.timezone.utc)
    return parsed.astimezone(dt.timezone.utc)


def location_is_fresh(location, now, max_age_seconds):
    if not isinstance(location, dict) or location.get("online") is False:
        return False
    collected_at = parse_api_time(location.get("collectedAt"))
    if collected_at is None:
        return False
    age = (now.astimezone(dt.timezone.utc) - collected_at).total_seconds()
    return -5 <= age <= max_age_seconds


def select_transporting_tasks(payload):
    data = unwrap_api_response(payload)
    records = data.get("records") if isinstance(data, dict) else None
    if not isinstance(records, list):
        raise ValueError("任务列表响应缺少data.records")
    result = []
    for task in records:
        if not isinstance(task, dict) or task.get("status") != "TRANSPORTING":
            continue
        if not isinstance(task.get("id"), int) or task["id"] <= 0:
            continue
        if not isinstance(task.get("vehicleId"), int) or task["vehicleId"] <= 0:
            continue
        result.append(task)
    return result


def route_fingerprint(task):
    return (
        task.get("startLongitude"),
        task.get("startLatitude"),
        task.get("endLongitude"),
        task.get("endLatitude"),
        task.get("vehicleId"),
    )


def route_coordinates_complete(task):
    return all(
        isinstance(task.get(field), (int, float))
        for field in (
            "startLongitude",
            "startLatitude",
            "endLongitude",
            "endLatitude",
        )
    )


def sim_start_index(vehicle_code):
    match = SIM_CODE_PATTERN.fullmatch(str(vehicle_code or "").strip())
    if match is None:
        return None
    return int(match.group(1))


class BusinessApiClient:
    def __init__(self, base_url, username, password, timeout=15):
        self.base_url = base_url.rstrip("/")
        if self.base_url.endswith("/api/v1"):
            self.api_root = self.base_url
        else:
            self.api_root = self.base_url + "/api/v1"
        self.username = username
        self.password = password
        self.timeout = timeout
        self.token = None

    def login(self):
        payload = self._raw_request(
            "POST",
            "/auth/login",
            {"username": self.username, "password": self.password},
            authenticated=False,
        )
        data = unwrap_api_response(payload)
        token = data.get("accessToken") if isinstance(data, dict) else None
        if not isinstance(token, str) or not token.strip():
            raise RuntimeError("登录响应缺少data.accessToken")
        self.token = token.strip()
        return self.token

    def request(self, method, path, body=None):
        if not self.token:
            self.login()
        try:
            return self._raw_request(method, path, body, authenticated=True)
        except urllib.error.HTTPError as exc:
            if exc.code != 401:
                raise
            self.login()
            return self._raw_request(method, path, body, authenticated=True)

    def _raw_request(self, method, path, body, authenticated):
        headers = {
            "Accept": "application/json",
            "User-Agent": "smart-logistics-gps-fallback/1.0",
        }
        encoded = None
        if body is not None:
            encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if authenticated and self.token:
            headers["Authorization"] = "Bearer " + self.token
        request = urllib.request.Request(
            self.api_root + path,
            data=encoded,
            headers=headers,
            method=method,
        )
        with urllib.request.urlopen(request, timeout=self.timeout) as response:
            return json.loads(response.read().decode("utf-8"))

    def list_tasks(self):
        return self.request("GET", "/transport-tasks?page=1&pageSize=100")

    def get_vehicle(self, vehicle_id):
        return unwrap_api_response(self.request("GET", f"/vehicles/{vehicle_id}"))

    def get_latest_location(self, vehicle_id):
        try:
            payload = self.request("GET", f"/vehicles/{vehicle_id}/location/latest")
        except urllib.error.HTTPError as exc:
            if exc.code in (404, 409):
                return None
            raise
        return unwrap_api_response(payload)

    def get_route(self, task_id):
        payload = self.request("GET", f"/transport-tasks/{task_id}/planned-route")
        return parse_task_route(payload)


class SingleInstanceLock:
    def __init__(self, path):
        self.path = pathlib.Path(path)
        self.handle = None

    def __enter__(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.handle = self.path.open("a+b")
        self.handle.seek(0, os.SEEK_END)
        if self.handle.tell() == 0:
            self.handle.write(b"0")
            self.handle.flush()
        self.handle.seek(0)
        try:
            if os.name == "nt":
                import msvcrt

                msvcrt.locking(self.handle.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl

                fcntl.flock(self.handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as exc:
            self.handle.close()
            self.handle = None
            raise RuntimeError("已有一个GPS自动兜底脚本正在运行") from exc
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        if self.handle is None:
            return
        try:
            self.handle.seek(0)
            if os.name == "nt":
                import msvcrt

                msvcrt.locking(self.handle.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                import fcntl

                fcntl.flock(self.handle.fileno(), fcntl.LOCK_UN)
        finally:
            self.handle.close()


@dataclass
class ManagedPublisher:
    task_id: int
    vehicle_code: str
    fingerprint: tuple
    process: subprocess.Popen
    stdout_handle: object
    stderr_handle: object

    def close_logs(self):
        self.stdout_handle.close()
        self.stderr_handle.close()


class FallbackManager:
    def __init__(self, client, simulator_path, mqtt_credentials, runtime_dir,
                 freshness_seconds=10, interval=1.0, anomaly_rate=0.0,
                 demo_anomaly=None, alert_mode="precomputed", dry_run=False):
        self.client = client
        self.simulator_path = pathlib.Path(simulator_path).resolve()
        self.mqtt_credentials = pathlib.Path(mqtt_credentials).resolve()
        self.runtime_dir = pathlib.Path(runtime_dir).resolve()
        self.freshness_seconds = freshness_seconds
        self.interval = interval
        self.anomaly_rate = anomaly_rate
        self.demo_anomaly = demo_anomaly
        self.alert_mode = alert_mode
        self.dry_run = dry_run
        self.publishers = {}
        self.last_task_messages = {}
        self.runtime_dir.mkdir(parents=True, exist_ok=True)

    def report_task_once(self, task_id, key, message):
        if self.last_task_messages.get(task_id) == key:
            return
        self.last_task_messages[task_id] = key
        print(message)

    def reconcile(self):
        tasks = select_transporting_tasks(self.client.list_tasks())
        tasks_by_id = {task["id"]: task for task in tasks}
        for task_id in set(self.last_task_messages) - set(tasks_by_id):
            self.last_task_messages.pop(task_id, None)

        for task_id, publisher in list(self.publishers.items()):
            task = tasks_by_id.get(task_id)
            changed = task is not None and route_fingerprint(task) != publisher.fingerprint
            exited = publisher.process.poll() is not None
            if task is None or changed or exited:
                reason = "任务已离开TRANSPORTING"
                if changed:
                    reason = "任务车辆或起终点已变化"
                elif exited:
                    reason = f"模拟器已退出，code={publisher.process.returncode}"
                self.stop_publisher(task_id, reason)

        used_vehicle_codes = {
            publisher.vehicle_code for publisher in self.publishers.values()
        }
        now = dt.datetime.now(dt.timezone.utc)
        for task in tasks:
            task_id = task["id"]
            if task_id in self.publishers:
                continue
            try:
                if not route_coordinates_complete(task):
                    self.report_task_once(
                        task_id,
                        "route-coordinates-incomplete",
                        f"[SKIP] task={task_id} 起终点坐标不完整，不能加载计划路线",
                    )
                    continue
                vehicle = self.client.get_vehicle(task["vehicleId"])
                vehicle_code = str(vehicle.get("simCode") or "").strip()
                start_index = sim_start_index(vehicle_code)
                if start_index is None:
                    self.report_task_once(
                        task_id,
                        "real-device:" + vehicle_code,
                        f"[SKIP] task={task_id} vehicle={vehicle_code or '-'} "
                        "不是sim_*设备，真实设备优先",
                    )
                    continue
                if vehicle_code in used_vehicle_codes:
                    self.report_task_once(
                        task_id,
                        "vehicle-already-managed:" + vehicle_code,
                        f"[SKIP] task={task_id} vehicle={vehicle_code} "
                        "已有本脚本管理的发布器",
                    )
                    continue
                latest = self.client.get_latest_location(task["vehicleId"])
                if location_is_fresh(latest, now, self.freshness_seconds):
                    self.report_task_once(
                        task_id,
                        "fresh-gps",
                        f"[LIVE] task={task_id} vehicle={vehicle_code} "
                        f"已有新鲜GPS collectedAt={latest.get('collectedAt')}，不启动模拟器",
                    )
                    continue
                route = self.client.get_route(task_id)
                if route["vehicle_id"] != vehicle_code:
                    raise ValueError("planned-route车辆编号与vehicle.simCode不一致")
                self.start_publisher(task, route, start_index)
                self.last_task_messages.pop(task_id, None)
                used_vehicle_codes.add(vehicle_code)
            except Exception as exc:
                key = f"error:{type(exc).__name__}:{exc}"
                self.report_task_once(
                    task_id,
                    key,
                    f"[ERROR] task={task_id} 检查或启动失败：{type(exc).__name__}: {exc}",
                )

        return len(tasks), len(self.publishers)

    def start_publisher(self, task, route, start_index):
        task_id = task["id"]
        vehicle_code = route["vehicle_id"]
        if self.dry_run:
            print(
                f"[DRY-RUN] task={task_id} vehicle={vehicle_code} "
                f"points={len(route['points'])} 将启动路线模拟器"
            )
            return

        stdout_path = self.runtime_dir / f"task_{task_id}_{vehicle_code}.log"
        stderr_path = self.runtime_dir / f"task_{task_id}_{vehicle_code}.err.log"
        output_path = self.runtime_dir / f"task_{task_id}_{vehicle_code}.jsonl"
        stdout_handle = stdout_path.open("a", encoding="utf-8")
        stderr_handle = stderr_path.open("a", encoding="utf-8")
        child_env = os.environ.copy()
        child_env["SMART_LOGISTICS_API_TOKEN"] = self.client.token
        command = [
            sys.executable,
            "-u",
            str(self.simulator_path),
            "--credentials",
            str(self.mqtt_credentials),
            "--business-api-base",
            self.client.base_url,
            "--task-id",
            str(task_id),
            "--vehicles",
            "1",
            "--vehicle-start-index",
            str(start_index),
            "--duration",
            "0",
            "--interval",
            str(self.interval),
            "--anomaly-rate",
            str(self.anomaly_rate),
            "--alert-mode",
            self.alert_mode,
            "--control-stdin",
            "--output",
            str(output_path),
        ]
        if self.demo_anomaly:
            command.extend(["--demo-anomaly", self.demo_anomaly])
        creation_flags = 0
        if os.name == "nt":
            creation_flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        try:
            process = subprocess.Popen(
                command,
                cwd=str(self.simulator_path.parent),
                env=child_env,
                stdin=subprocess.PIPE,
                stdout=stdout_handle,
                stderr=stderr_handle,
                text=True,
                encoding="utf-8",
                creationflags=creation_flags,
            )
        except Exception:
            stdout_handle.close()
            stderr_handle.close()
            raise
        self.publishers[task_id] = ManagedPublisher(
            task_id,
            vehicle_code,
            route_fingerprint(task),
            process,
            stdout_handle,
            stderr_handle,
        )
        print(
            f"[START] task={task_id} vehicle={vehicle_code} pid={process.pid} "
            f"points={len(route['points'])} log={stdout_path}"
        )

    def inject_anomaly(self, anomaly_type, vehicle_code=None):
        if anomaly_type not in {"stop", "drift", "open", "close", "resume"}:
            raise ValueError(f"不支持的异常控制指令：{anomaly_type}")
        injected = []
        for publisher in list(self.publishers.values()):
            if vehicle_code and publisher.vehicle_code != vehicle_code:
                continue
            process = publisher.process
            if process.poll() is not None or process.stdin is None:
                continue
            try:
                process.stdin.write(anomaly_type + "\n")
                process.stdin.flush()
                injected.append(publisher.vehicle_code)
            except (BrokenPipeError, OSError, ValueError):
                continue
        if injected:
            print(
                f"[ALERT][CONTROL] type={anomaly_type} vehicles={','.join(injected)}"
            )
        else:
            target = f" vehicle={vehicle_code}" if vehicle_code else ""
            managed = ",".join(
                sorted(publisher.vehicle_code for publisher in self.publishers.values())
            ) or "-"
            print(
                f"[ALERT][SKIP]{target} 当前没有可控制的目标；"
                f"本脚本车辆={managed}"
            )
        return injected

    def stop_publisher(self, task_id, reason):
        publisher = self.publishers.pop(task_id, None)
        if publisher is None:
            return
        process = publisher.process
        if process.stdin is not None:
            try:
                process.stdin.close()
            except OSError:
                pass
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
        publisher.close_logs()
        self.last_task_messages.pop(task_id, None)
        print(
            f"[STOP] task={task_id} vehicle={publisher.vehicle_code} reason={reason}"
        )

    def stop_all(self):
        for task_id in list(self.publishers):
            self.stop_publisher(task_id, "兜底脚本停止")


def build_parser():
    parser = argparse.ArgumentParser(
        description="真实GPS优先的TransportTask模拟GPS自动兜底脚本"
    )
    parser.add_argument("--business-api-base", required=True, help="业务后端地址")
    parser.add_argument("--business-username", default="eta_service")
    parser.add_argument(
        "--business-password-env",
        default="SMART_LOGISTICS_API_PASSWORD",
        help="保存业务后端密码的环境变量名",
    )
    parser.add_argument("--mqtt-credentials", required=True, type=pathlib.Path)
    parser.add_argument("--poll-seconds", type=float, default=2.0)
    parser.add_argument("--fresh-gps-seconds", type=float, default=10.0)
    parser.add_argument("--gps-interval", type=float, default=1.0)
    parser.add_argument(
        "--anomaly-rate",
        type=float,
        default=0.0,
        help="每辆模拟车每批随机注入异常的概率；默认0表示关闭",
    )
    parser.add_argument(
        "--demo-anomaly",
        choices=("stop", "drift", "open"),
        help="每个新启动的任务发布器在首批只注入一次指定异常",
    )
    parser.add_argument(
        "--alert-mode",
        choices=("precomputed", "raw"),
        default="precomputed",
        help="precomputed直接发标准告警；raw仅制造可由实时后端检测的GPS异常",
    )
    parser.add_argument("--runtime-dir", type=pathlib.Path)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--once", action="store_true", help="只扫描一次后退出")
    return parser


def process_console_hotkeys(manager):
    if os.name != "nt" or not sys.stdin.isatty():
        return
    import msvcrt

    key_to_anomaly = {
        "o": "open",
        "c": "close",
        "s": "stop",
        "r": "resume",
        "d": "drift",
    }
    while msvcrt.kbhit():
        key = msvcrt.getwch().lower()
        anomaly_type = key_to_anomaly.get(key)
        if anomaly_type:
            manager.inject_anomaly(anomaly_type)
        elif key == "t":
            process_target_console_command(manager)
        elif key == "h":
            print("[HOTKEY] O=全车开箱 C=全车关箱 S=全车停留 R=全车恢复 D=全车偏航 T=指定车辆 H=帮助 Ctrl+C=停止")


def process_target_console_command(manager):
    try:
        raw = input(
            "\n[TARGET] 输入命令，例如 S sim_002、O sim_001、R sim_002："
        ).strip()
    except (EOFError, KeyboardInterrupt):
        print("\n[TARGET] 已取消")
        return
    parts = raw.split()
    actions = {
        "o": "open",
        "c": "close",
        "s": "stop",
        "r": "resume",
        "d": "drift",
    }
    if len(parts) != 2 or parts[0].lower() not in actions:
        print("[TARGET][ERROR] 格式应为：O|C|S|R|D sim_000~sim_999")
        return
    vehicle_code = parts[1].lower()
    if SIM_CODE_PATTERN.fullmatch(vehicle_code) is None:
        print("[TARGET][ERROR] 车辆编号格式应为sim_000~sim_999")
        return
    manager.inject_anomaly(actions[parts[0].lower()], vehicle_code=vehicle_code)


def main():
    parser = build_parser()
    args = parser.parse_args()
    if args.poll_seconds <= 0:
        parser.error("--poll-seconds必须大于0")
    if args.fresh_gps_seconds < 0:
        parser.error("--fresh-gps-seconds不能小于0")
    if args.gps_interval <= 0:
        parser.error("--gps-interval必须大于0")
    if not 0 <= args.anomaly_rate <= 1:
        parser.error("--anomaly-rate必须在0到1之间")
    if args.demo_anomaly == "open" and args.alert_mode == "raw":
        parser.error("异常开箱没有GPS原始特征，必须使用--alert-mode precomputed")
    password = os.environ.get(args.business_password_env)
    if not password:
        parser.error(f"环境变量{args.business_password_env}未设置")
    if not args.mqtt_credentials.is_file():
        parser.error(f"MQTT凭据文件不存在：{args.mqtt_credentials}")

    script_dir = pathlib.Path(__file__).resolve().parent
    runtime_dir = args.runtime_dir or script_dir / "runtime" / "task-gps-fallback"
    client = BusinessApiClient(
        args.business_api_base,
        args.business_username,
        password,
    )
    manager = FallbackManager(
        client,
        script_dir / "mqtt_data_generator.py",
        args.mqtt_credentials,
        runtime_dir,
        freshness_seconds=args.fresh_gps_seconds,
        interval=args.gps_interval,
        anomaly_rate=args.anomaly_rate,
        demo_anomaly=args.demo_anomaly,
        alert_mode=args.alert_mode,
        dry_run=args.dry_run,
    )
    lock_path = runtime_dir / "task-gps-fallback.lock"
    print(
        f"[READY] 后端={args.business_api_base} poll={args.poll_seconds}s "
        f"freshGPS={args.fresh_gps_seconds}s anomalyRate={args.anomaly_rate} "
        f"demoAnomaly={args.demo_anomaly or '-'} alertMode={args.alert_mode}；"
        "只读任务，不修改状态"
    )
    if os.name == "nt" and sys.stdin.isatty():
        print("[HOTKEY] O=全车开箱 C=全车关箱 S=全车停留 R=全车恢复 D=全车偏航 T=指定车辆 H=帮助 Ctrl+C=停止")
    try:
        with SingleInstanceLock(lock_path):
            last_summary = None
            while True:
                try:
                    transporting, running = manager.reconcile()
                    summary = (transporting, running)
                    if summary != last_summary:
                        print(
                            f"[SCAN] TRANSPORTING={transporting} "
                            f"本脚本发布器={running}"
                        )
                        last_summary = summary
                    if args.once:
                        break
                except Exception as exc:
                    print(f"[ERROR] 本轮扫描失败：{type(exc).__name__}: {exc}")
                    if args.once:
                        break
                deadline = time.monotonic() + args.poll_seconds
                while time.monotonic() < deadline:
                    process_console_hotkeys(manager)
                    time.sleep(min(0.1, max(0, deadline - time.monotonic())))
    except KeyboardInterrupt:
        print("\n[SHUTDOWN] 正在停止本脚本启动的模拟器...")
    finally:
        manager.stop_all()


if __name__ == "__main__":
    main()
