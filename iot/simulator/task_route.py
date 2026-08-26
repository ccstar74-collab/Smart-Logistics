# -*- coding: utf-8 -*-
"""Fetch and validate a backend-owned transport-task route."""

import json
import urllib.request

from route_planner import parse_polyline


ROUTE_STATUSES = {"PLANNING", "READY", "FAILED"}


class RouteNotReadyError(RuntimeError):
    def __init__(self, status, message=None):
        self.status = status
        super().__init__(message or f"任务路线状态为{status}")


def _positive_integer(value, field_name):
    if isinstance(value, bool):
        raise ValueError(f"{field_name}必须是正整数")
    try:
        parsed = int(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{field_name}必须是正整数") from exc
    if parsed <= 0:
        raise ValueError(f"{field_name}必须是正整数")
    return parsed


def _unwrap_api_response(payload):
    if not isinstance(payload, dict):
        raise ValueError("路线接口响应必须是JSON对象")
    if "data" in payload:
        if payload.get("code") not in (None, 0, 200):
            raise RuntimeError(
                "路线接口返回失败：%s" % (payload.get("message") or payload.get("code"))
            )
        payload = payload.get("data")
    if not isinstance(payload, dict):
        raise ValueError("路线接口data必须是对象")
    return payload


def parse_task_route(payload):
    route = _unwrap_api_response(payload)
    task_id = _positive_integer(route.get("taskId"), "taskId")
    route_id = route.get("routeId")
    vehicle_code = route.get("vehicleDeviceCode")
    route_version = _positive_integer(route.get("routeVersion"), "routeVersion")
    route_status = str(route.get("routeStatus") or "").upper()

    if not isinstance(route_id, str) or not route_id.strip():
        raise ValueError("routeId不能为空")
    if not isinstance(vehicle_code, str) or not vehicle_code.strip():
        raise ValueError("vehicleDeviceCode不能为空")
    if route_status not in ROUTE_STATUSES:
        raise ValueError("routeStatus必须为PLANNING、READY或FAILED")
    if route_status != "READY":
        failure_reason = route.get("failureReason")
        raise RouteNotReadyError(route_status, failure_reason)

    total_distance = _positive_integer(
        route.get("totalDistanceMeters"), "totalDistanceMeters"
    )
    estimated_duration = _positive_integer(
        route.get("estimatedDurationSeconds"), "estimatedDurationSeconds"
    )
    points = parse_polyline(route.get("polyline"), route.get("coordinateSystem"))
    return {
        "task_id": task_id,
        "route_id": route_id.strip(),
        "route_version": route_version,
        "route_status": route_status,
        "vehicle_id": vehicle_code.strip(),
        "coordinate_system": str(route.get("coordinateSystem")).upper(),
        "total_distance_meters": total_distance,
        "estimated_duration_seconds": estimated_duration,
        "points": points,
    }


def build_route_url(api_base, task_id):
    if not isinstance(api_base, str) or not api_base.strip():
        raise ValueError("业务后端API地址不能为空")
    task_id = _positive_integer(task_id, "taskId")
    base = api_base.rstrip("/")
    if base.endswith("/api/v1"):
        return f"{base}/transport-tasks/{task_id}/route"
    return f"{base}/api/v1/transport-tasks/{task_id}/route"


def fetch_task_route(api_base, task_id, token=None, timeout=15):
    route_url = build_route_url(api_base, task_id)
    headers = {"Accept": "application/json", "User-Agent": "smart-logistics-demo/1.0"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(route_url, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except Exception as exc:
        # Do not include the Authorization header or token in diagnostics.
        raise RuntimeError(f"读取业务路线接口失败：{type(exc).__name__}") from exc
    parsed = parse_task_route(payload)
    if parsed["task_id"] != int(task_id):
        raise ValueError("路线接口返回的taskId与请求不一致")
    return parsed
