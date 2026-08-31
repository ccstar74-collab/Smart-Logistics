# -*- coding: utf-8 -*-
"""Fetch and validate the ETA-owned transport-task planned route."""

import json
import urllib.request

from route_planner import parse_route_points


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
        raise ValueError("规划路线接口响应必须是JSON对象")
    if "data" in payload:
        if payload.get("code") not in (None, 0, 200):
            raise RuntimeError(
                "规划路线接口返回失败：%s"
                % (payload.get("message") or payload.get("code"))
            )
        payload = payload.get("data")
    if not isinstance(payload, dict):
        raise ValueError("规划路线接口data必须是对象")
    return payload


def parse_task_route(payload):
    route = _unwrap_api_response(payload)
    task_id = _positive_integer(route.get("taskId"), "taskId")
    vehicle_code = route.get("vehicleDeviceCode")
    provider = str(route.get("provider") or "").upper()
    coordinate_system = str(route.get("coordinateSystem") or "").upper()
    generated_at = route.get("generatedAt")

    if not isinstance(vehicle_code, str) or not vehicle_code.strip():
        raise ValueError("vehicleDeviceCode不能为空")
    if provider != "AMAP":
        raise ValueError("planned-route.provider必须为AMAP")
    if not isinstance(generated_at, str) or not generated_at.strip():
        raise ValueError("generatedAt不能为空")

    distance_meters = _positive_integer(route.get("distanceMeters"), "distanceMeters")
    reference_duration = _positive_integer(
        route.get("referenceDurationSeconds"), "referenceDurationSeconds"
    )
    points = parse_route_points(route.get("points"), coordinate_system)
    route_id = route.get("routeId")
    if not isinstance(route_id, str) or not route_id.strip():
        route_id = f"TASK_{task_id}_{generated_at.strip()}"
    raw_version = route.get("routeVersion", 1)
    route_version = _positive_integer(raw_version, "routeVersion")
    route_status = str(route.get("routeStatus") or "READY").upper()
    if route_status not in {"READY", "ACTIVE"}:
        raise ValueError("planned-route.routeStatus必须为READY或ACTIVE")

    return {
        "task_id": task_id,
        "route_id": route_id.strip(),
        "route_version": route_version,
        "route_status": route_status,
        "vehicle_id": vehicle_code.strip(),
        "coordinate_system": coordinate_system,
        "total_distance_meters": distance_meters,
        "estimated_duration_seconds": reference_duration,
        "generated_at": generated_at.strip(),
        "points": points,
    }


def build_route_url(api_base, task_id):
    if not isinstance(api_base, str) or not api_base.strip():
        raise ValueError("后端API地址不能为空")
    task_id = _positive_integer(task_id, "taskId")
    base = api_base.rstrip("/")
    if base.endswith("/api/v1"):
        return f"{base}/transport-tasks/{task_id}/planned-route"
    return f"{base}/api/v1/transport-tasks/{task_id}/planned-route"


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
        # 诊断信息不得包含 Authorization 或 Token。
        raise RuntimeError(f"读取planned-route接口失败：{type(exc).__name__}") from exc
    parsed = parse_task_route(payload)
    if parsed["task_id"] != int(task_id):
        raise ValueError("planned-route返回的taskId与请求不一致")
    return parsed


def replan_task_route(api_base, task_id, token=None, position=None, timeout=30):
    """Ask the business backend to plan current WGS84 GPS -> task destination."""
    base = api_base.rstrip("/")
    task_id = _positive_integer(task_id, "taskId")
    if base.endswith("/api/v1"):
        route_url = (
            f"{base}/transport-tasks/{task_id}"
            "/routes/replan-from-latest-location"
        )
    else:
        route_url = (
            f"{base}/api/v1/transport-tasks/{task_id}"
            "/routes/replan-from-latest-location"
        )
    headers = {
        "Accept": "application/json",
        "Content-Type": "application/json",
        "User-Agent": "smart-logistics-demo/1.0",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body = position if isinstance(position, dict) else {}
    request = urllib.request.Request(
        route_url,
        data=json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except Exception as exc:
        raise RuntimeError(
            f"请求偏航重规划接口失败：{type(exc).__name__}"
        ) from exc
    parsed = parse_task_route(payload)
    if parsed["task_id"] != task_id:
        raise ValueError("重规划接口返回的taskId与请求不一致")
    return parsed
