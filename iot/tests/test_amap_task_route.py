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
    load_road_route,
    wgs84_to_gcj02,
)
from task_route import parse_task_route_context  # noqa: E402


class _JsonResponse:
    def __init__(self, payload):
        self.payload = payload

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return False

    def read(self):
        return json.dumps(self.payload).encode("utf-8")


class AmapRouteTest(unittest.TestCase):
    def test_wgs84_gcj02_round_trip_is_close(self):
        original = (106.730553, 29.613528)
        gcj = wgs84_to_gcj02(*original)
        restored = gcj02_to_wgs84(*gcj)
        self.assertNotEqual(original, gcj)
        self.assertAlmostEqual(original[0], restored[0], delta=0.00001)
        self.assertAlmostEqual(original[1], restored[1], delta=0.00001)

    def test_amap_polyline_is_returned_as_wgs84_round_trip(self):
        wgs_points = [
            (106.730553, 29.613528),
            (106.742000, 29.618000),
            (106.754928, 29.622890),
        ]
        gcj_points = [wgs84_to_gcj02(*point) for point in wgs_points]
        polyline = ";".join("%.6f,%.6f" % point for point in gcj_points)
        response = {
            "status": "1",
            "info": "OK",
            "route": {
                "paths": [{"distance": "3200", "steps": [{"polyline": polyline}]}]
            },
        }
        with patch("route_planner.urllib.request.urlopen", return_value=_JsonResponse(response)):
            points = load_road_route(
                "106.730553,29.613528;106.754928,29.622890",
                amap_key="test-only-key",
            )
        self.assertEqual(4, len(points))
        self.assertAlmostEqual(wgs_points[0][0], points[0][1], delta=0.00001)
        self.assertAlmostEqual(wgs_points[0][1], points[0][0], delta=0.00001)


class TaskRouteContextTest(unittest.TestCase):
    def test_recommended_business_dto_is_normalized(self):
        context = parse_task_route_context({
            "code": 200,
            "message": "success",
            "data": {
                "taskId": 1001,
                "vehicleId": 1,
                "vehicleDeviceCode": "sim_000",
                "coordinateSystem": "WGS84",
                "origin": {
                    "name": "果园港装卸点A",
                    "longitude": 106.730553,
                    "latitude": 29.613528,
                },
                "destination": {
                    "name": "工业园配送点A",
                    "longitude": 106.754928,
                    "latitude": 29.622890,
                },
            },
        })
        self.assertEqual(1001, context["task_id"])
        self.assertEqual("sim_000", context["vehicle_id"])
        self.assertEqual(2, len(context["waypoints"]))
        self.assertEqual(106.730553, context["waypoints"][0]["lon"])

    def test_legacy_locations_can_be_geocoded(self):
        with patch("task_route.geocode_amap") as geocode:
            geocode.side_effect = [
                (106.730553, 29.613528),
                (106.754928, 29.622890),
            ]
            context = parse_task_route_context({
                "code": 200,
                "data": {
                    "id": 1001,
                    "startLocation": "重庆果园港",
                    "endLocation": "重庆鱼嘴工业园",
                },
            }, amap_key="test-only-key", city="重庆")
        self.assertEqual(2, geocode.call_count)
        self.assertEqual(106.754928, context["waypoints"][1]["lon"])


if __name__ == "__main__":
    unittest.main()
