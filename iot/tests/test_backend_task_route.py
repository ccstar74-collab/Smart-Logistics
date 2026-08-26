# -*- coding: utf-8 -*-

import json
import pathlib
import sys
import unittest
from unittest.mock import patch


SIMULATOR_DIR = pathlib.Path(__file__).resolve().parents[1] / "simulator"
sys.path.insert(0, str(SIMULATOR_DIR))

from route_planner import (  # noqa: E402
    gcj02_to_wgs84,
    parse_route_points,
    wgs84_to_gcj02,
)
from task_route import build_route_url, fetch_task_route, parse_task_route  # noqa: E402
from mqtt_data_generator import advance_on_route, install_task_route  # noqa: E402


class _JsonResponse:
    def __init__(self, payload):
        self.payload = payload

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return False

    def read(self):
        return json.dumps(self.payload).encode("utf-8")


def planned_route_response():
    return {
        "code": 200,
        "message": "success",
        "data": {
            "taskId": 1001,
            "vehicleDeviceCode": "sim_000",
            "provider": "AMAP",
            "coordinateSystem": "GCJ02",
            "distanceMeters": 2943,
            "referenceDurationSeconds": 420,
            "generatedAt": "2026-08-26T16:00:00+08:00",
            "points": [
                {"longitude": 106.735012, "latitude": 29.610634},
                {"longitude": 106.741312, "latitude": 29.615804},
                {"longitude": 106.751240, "latitude": 29.618910},
                {"longitude": 106.759396, "latitude": 29.620115},
            ],
        },
    }


class BackendRouteTest(unittest.TestCase):
    def test_wgs84_gcj02_round_trip_is_within_gps_noise(self):
        original = (106.730553, 29.613528)
        gcj = wgs84_to_gcj02(*original)
        restored = gcj02_to_wgs84(*gcj)
        self.assertAlmostEqual(original[0], restored[0], delta=0.00001)
        self.assertAlmostEqual(original[1], restored[1], delta=0.00001)

    def test_gcj02_points_are_normalized_to_wgs84(self):
        points = parse_route_points(
            [
                {"longitude": 106.735012, "latitude": 29.610634},
                {"longitude": 106.741312, "latitude": 29.615804},
            ],
            "GCJ02",
        )
        self.assertEqual(2, len(points))
        self.assertNotEqual(106.735012, points[0][1])

    def test_planned_route_response_is_parsed(self):
        route = parse_task_route(planned_route_response())
        self.assertEqual(1001, route["task_id"])
        self.assertEqual("sim_000", route["vehicle_id"])
        self.assertEqual(2943, route["total_distance_meters"])
        self.assertEqual(420, route["estimated_duration_seconds"])
        self.assertEqual(4, len(route["points"]))

    def test_missing_vehicle_device_code_is_rejected(self):
        payload = planned_route_response()
        payload["data"]["vehicleDeviceCode"] = None
        with self.assertRaisesRegex(ValueError, "vehicleDeviceCode"):
            parse_task_route(payload)

    def test_route_url_accepts_host_or_api_v1_base(self):
        expected = "http://server:8080/api/v1/transport-tasks/1001/planned-route"
        self.assertEqual(expected, build_route_url("http://server:8080", 1001))
        self.assertEqual(expected, build_route_url("http://server:8080/api/v1/", 1001))

    def test_fetch_uses_bearer_and_validates_task_id(self):
        response = _JsonResponse(planned_route_response())
        with patch("task_route.urllib.request.urlopen", return_value=response) as opened:
            route = fetch_task_route("http://server:8080", 1001, token="test-token")
        request = opened.call_args.args[0]
        self.assertEqual("Bearer test-token", request.get_header("Authorization"))
        self.assertEqual("sim_000", route["vehicle_id"])

    def test_vehicle_stops_at_destination_instead_of_looping(self):
        vehicle = {
            "lat": 29.600000,
            "lon": 106.700000,
            "speed_kmh": 30.0,
            "heading": 0.0,
            "transport_status": "运输中",
            "route_points": None,
            "route_complete": False,
            "active_task_id": None,
            "route_id": None,
            "route_version": 0,
        }
        route = {
            "task_id": 1001,
            "route_id": "TASK_1001_2026-08-26T16:00:00+08:00",
            "route_version": 1,
            "points": [(29.600000, 106.700000), (29.600100, 106.700100)],
        }
        self.assertTrue(install_task_route(vehicle, route))
        advance_on_route(vehicle, route["points"], 1000)
        self.assertTrue(vehicle["route_complete"])
        self.assertEqual(0.0, vehicle["speed_kmh"])
        self.assertEqual("已送达", vehicle["transport_status"])
        self.assertAlmostEqual(29.600100, vehicle["lat"])

    def test_same_generated_route_is_ignored(self):
        vehicle = {
            "lat": 29.600000,
            "lon": 106.700000,
            "speed_kmh": 30.0,
            "heading": 0.0,
            "transport_status": "运输中",
            "route_points": None,
            "route_complete": False,
            "active_task_id": None,
            "route_id": None,
            "route_version": 0,
        }
        route = {
            "task_id": 1001,
            "route_id": "TASK_1001_2026-08-26T16:00:00+08:00",
            "route_version": 1,
            "points": [(29.600000, 106.700000), (29.601000, 106.701000)],
        }
        self.assertTrue(install_task_route(vehicle, route))
        self.assertFalse(install_task_route(vehicle, route))


if __name__ == "__main__":
    unittest.main()
