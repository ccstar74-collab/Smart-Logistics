# -*- coding: utf-8 -*-

import datetime as dt
import pathlib
import sys
import tempfile
import unittest
from unittest.mock import patch


SIMULATOR_DIR = pathlib.Path(__file__).resolve().parents[1] / "simulator"
sys.path.insert(0, str(SIMULATOR_DIR))

from task_gps_fallback import (  # noqa: E402
    FallbackManager,
    location_is_fresh,
    select_transporting_tasks,
    sim_start_index,
)


def task(task_id=9, status="TRANSPORTING", vehicle_id=23):
    return {
        "id": task_id,
        "status": status,
        "vehicleId": vehicle_id,
        "startLongitude": 106.55,
        "startLatitude": 29.60,
        "endLongitude": 106.44,
        "endLatitude": 29.50,
    }


def route(task_id=9, vehicle_code="sim_019"):
    return {
        "task_id": task_id,
        "vehicle_id": vehicle_code,
        "points": [(29.60, 106.55), (29.50, 106.44)],
    }


class FakeClient:
    def __init__(self, records, vehicle_code="sim_019", latest=None):
        self.records = records
        self.vehicle_code = vehicle_code
        self.latest = latest
        self.token = "test-token"
        self.base_url = "http://server:8080"
        self.route_calls = 0

    def list_tasks(self):
        return {"code": 200, "data": {"records": self.records}}

    def get_vehicle(self, vehicle_id):
        return {"id": vehicle_id, "simCode": self.vehicle_code}

    def get_latest_location(self, vehicle_id):
        return self.latest

    def get_route(self, task_id):
        self.route_calls += 1
        return route(task_id, self.vehicle_code)


class FakeProcess:
    def __init__(self):
        self.pid = 1234
        self.returncode = None
        self.terminated = False

    def poll(self):
        return self.returncode

    def terminate(self):
        self.terminated = True
        self.returncode = 0

    def wait(self, timeout=None):
        return self.returncode

    def kill(self):
        self.returncode = -9


class TaskGpsFallbackTest(unittest.TestCase):
    def test_only_transporting_tasks_are_selected(self):
        selected = select_transporting_tasks(
            {"code": 200, "data": {"records": [task(9), task(8, "WAITING")]}}
        )
        self.assertEqual([9], [item["id"] for item in selected])

    def test_sim_start_index(self):
        self.assertEqual(19, sim_start_index("sim_019"))
        self.assertIsNone(sim_start_index("real_001"))

    def test_fresh_location_is_detected(self):
        now = dt.datetime(2026, 8, 27, 2, 0, tzinfo=dt.timezone.utc)
        location = {
            "online": True,
            "collectedAt": "2026-08-27T09:59:55+08:00",
        }
        self.assertTrue(location_is_fresh(location, now, 10))
        self.assertFalse(location_is_fresh(location, now, 3))

    def test_fresh_gps_prevents_route_request_and_simulator_start(self):
        now = dt.datetime.now(dt.timezone.utc)
        client = FakeClient(
            [task()],
            latest={"online": True, "collectedAt": now.isoformat()},
        )
        with tempfile.TemporaryDirectory() as directory:
            manager = FallbackManager(
                client,
                SIMULATOR_DIR / "mqtt_data_generator.py",
                pathlib.Path(directory) / "mqtt.env",
                pathlib.Path(directory) / "runtime",
            )
            transporting, running = manager.reconcile()
        self.assertEqual(1, transporting)
        self.assertEqual(0, running)
        self.assertEqual(0, client.route_calls)

    def test_real_device_never_starts_simulator(self):
        client = FakeClient([task()], vehicle_code="real_001", latest=None)
        with tempfile.TemporaryDirectory() as directory:
            manager = FallbackManager(
                client,
                SIMULATOR_DIR / "mqtt_data_generator.py",
                pathlib.Path(directory) / "mqtt.env",
                pathlib.Path(directory) / "runtime",
            )
            manager.reconcile()
        self.assertEqual(0, client.route_calls)

    def test_incomplete_route_coordinates_are_skipped_before_api_calls(self):
        incomplete = task()
        incomplete["startLongitude"] = None
        client = FakeClient([incomplete], latest=None)
        with tempfile.TemporaryDirectory() as directory:
            manager = FallbackManager(
                client,
                SIMULATOR_DIR / "mqtt_data_generator.py",
                pathlib.Path(directory) / "mqtt.env",
                pathlib.Path(directory) / "runtime",
            )
            manager.reconcile()
            manager.reconcile()
        self.assertEqual(0, client.route_calls)

    def test_stale_sim_vehicle_starts_exactly_one_vehicle(self):
        client = FakeClient([task()], latest=None)
        fake_process = FakeProcess()
        with tempfile.TemporaryDirectory() as directory, patch(
            "task_gps_fallback.subprocess.Popen", return_value=fake_process
        ) as popen:
            manager = FallbackManager(
                client,
                SIMULATOR_DIR / "mqtt_data_generator.py",
                pathlib.Path(directory) / "mqtt.env",
                pathlib.Path(directory) / "runtime",
            )
            transporting, running = manager.reconcile()
            command = popen.call_args.args[0]
            self.assertEqual(1, transporting)
            self.assertEqual(1, running)
            self.assertIn("--vehicles", command)
            self.assertEqual("1", command[command.index("--vehicles") + 1])
            self.assertEqual("19", command[command.index("--vehicle-start-index") + 1])
            manager.stop_all()
        self.assertTrue(fake_process.terminated)


if __name__ == "__main__":
    unittest.main()
