# -*- coding: utf-8 -*-

import json
import pathlib
import sys
import unittest
from unittest.mock import patch


SIMULATOR_DIR = pathlib.Path(__file__).resolve().parents[1] / "simulator"
sys.path.insert(0, str(SIMULATOR_DIR))

from route_planner import gcj02_to_wgs84, parse_polyline, wgs84_to_gcj02  # noqa: E402
from task_route import (  # noqa: E402
    RouteNotReadyError,
    build_route_url,
    fetch_task_route,
    parse_task_route,
)
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


def ready_response(version=1):
    return {
        "code": 200,
        "message": "success",
        "data": {
            "taskId": 1001,
            "routeId": "ROUTE_1001",
            "routeVersion": version,
            "routeStatus": "READY",
            "vehicleId": 1,
            "vehicleDeviceCode": "sim_000",
            "coordinateSystem": "GCJ02",
            "totalDistanceMeters": 2943,
            "estimatedDurationSeconds": 420,
            "polyline": (
                "106.735012,29.610634;106.741312,29.615804;"
                "106.751240,29.618910;106.759396,29.620115"
            ),
        },
    }


class BackendRouteTest(unittest.TestCase):
    def test_wgs84_gcj02_round_trip_is_within_gps_noise(self):
        original = (106.730553, 29.613528)
        gcj = wgs84_to_gcj02(*original)
        restored = gcj02_to_wgs84(*gcj)
        self.assertAlmostEqual(original[0], restored[0], delta=0.00001)
        self.assertAlmostEqual(original[1], restored[1], delta=0.00001)

    def test_gcj02_polyline_is_normalized_to_wgs84(self):
        points = parse_polyline(
            "106.735012,29.610634;106.741312,29.615804", "GCJ02"
        )
        self.assertEqual(2, len(points))
        self.assertNotEqual(106.735012, points[0][1])

    def test_ready_route_response_is_parsed(self):
        route = parse_task_route(ready_response())
        self.assertEqual(1001, route["task_id"])
        self.assertEqual("ROUTE_1001", route["route_id"])
        self.assertEqual("sim_000", route["vehicle_id"])
        self.assertEqual(4, len(route["points"]))

    def test_planning_route_is_not_treated_as_ready(self):
        payload = ready_response()
        payload["data"].update({"routeStatus": "PLANNING", "polyline": None})
        with self.assertRaises(RouteNotReadyError) as raised:
            parse_task_route(payload)
        self.assertEqual("PLANNING", raised.exception.status)

    def test_route_url_accepts_host_or_api_v1_base(self):
        expected = "http://server:8080/api/v1/transport-tasks/1001/route"
        self.assertEqual(expected, build_route_url("http://server:8080", 1001))
        self.assertEqual(expected, build_route_url("http://server:8080/api/v1/", 1001))

    def test_fetch_uses_bearer_and_validates_task_id(self):
        response = _JsonResponse(ready_response())
        with patch("task_route.urllib.request.urlopen", return_value=response) as opened:
            route = fetch_task_route("http://server:8080", 1001, token="test-token")
        request = opened.call_args.args[0]
        self.assertEqual("Bearer test-token", request.get_header("Authorization"))
        self.assertEqual(1, route["route_version"])

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
            "route_id": "ROUTE_1001",
            "route_version": 1,
            "points": [(29.600000, 106.700000), (29.600100, 106.700100)],
        }
        self.assertTrue(install_task_route(vehicle, route))
        advance_on_route(vehicle, route["points"], 1000)
        self.assertTrue(vehicle["route_complete"])
        self.assertEqual(0.0, vehicle["speed_kmh"])
        self.assertEqual("已送达", vehicle["transport_status"])
        self.assertAlmostEqual(29.600100, vehicle["lat"])

    def test_same_or_older_route_version_is_ignored(self):
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
            "route_id": "ROUTE_1001",
            "route_version": 2,
            "points": [(29.600000, 106.700000), (29.601000, 106.701000)],
        }
        self.assertTrue(install_task_route(vehicle, route))
        older = dict(route, route_version=1)
        self.assertFalse(install_task_route(vehicle, older))
        self.assertEqual(2, vehicle["route_version"])


if __name__ == "__main__":
    unittest.main()
