# -*- coding: utf-8 -*-
"""Load a business transport task and normalize its route to WGS84."""

import json
import urllib.request

from route_planner import gcj02_to_wgs84, geocode_amap


def _normalized_coordinate_system(value):
    normalized = str(value or "WGS84").upper().replace("-", "").replace("_", "")
    if normalized in ("WGS84", "EPSG4326"):
        return "WGS84"
    if normalized in ("GCJ02", "AMAP"):
        return "GCJ02"
    raise ValueError(f"不支持的坐标系：{value}")


def _point_from_object(value):
    if not isinstance(value, dict):
        return None
    lon = value.get("longitude", value.get("lon"))
    lat = value.get("latitude", value.get("lat"))
    if lon is None or lat is None:
        return None
    try:
        lon = float(lon)
        lat = float(lat)
    except (TypeError, ValueError) as exc:
        raise ValueError("任务接口中的经纬度必须是数字") from exc
    if not -180 <= lon <= 180 or not -90 <= lat <= 90:
        raise ValueError("任务接口中的经纬度超出范围")
    return lon, lat


def _flat_point(task, prefix):
    title = prefix[0].upper() + prefix[1:]
    return _point_from_object({
        "longitude": task.get(f"{prefix}Longitude", task.get(f"{prefix}Lon")),
        "latitude": task.get(f"{prefix}Latitude", task.get(f"{prefix}Lat")),
        "name": task.get(f"{prefix}Location", task.get(f"{title}Location")),
    })


def _convert_point(point, coordinate_system):
    if coordinate_system == "GCJ02":
        return gcj02_to_wgs84(*point)
    return point


def _unwrap_api_response(payload):
    if not isinstance(payload, dict):
        raise ValueError("任务接口响应必须是JSON对象")
    if "data" in payload:
        if payload.get("code") not in (None, 0, 200):
            raise RuntimeError(
                "任务接口返回失败：%s" % (payload.get("message") or payload.get("code"))
            )
        payload = payload.get("data")
    if not isinstance(payload, dict):
        raise ValueError("任务接口data必须是任务对象")
    return payload


def parse_task_route_context(payload, amap_key=None, city=None):
    """Accept the recommended DTO plus compatible legacy field shapes."""
    task = _unwrap_api_response(payload)
    task_id = task.get("taskId", task.get("id"))
    try:
        task_id = int(task_id)
    except (TypeError, ValueError) as exc:
        raise ValueError("任务接口缺少有效的taskId/id") from exc
    if task_id <= 0:
        raise ValueError("任务ID必须大于0")

    vehicle = task.get("vehicle") if isinstance(task.get("vehicle"), dict) else {}
    vehicle_code = (
        task.get("vehicleDeviceCode")
        or task.get("deviceCode")
        or vehicle.get("deviceCode")
    )

    origin_object = task.get("origin") or task.get("pickup")
    destination_object = task.get("destination") or task.get("delivery")
    default_coordinate_system = task.get("coordinateSystem") or "WGS84"
    origin_coordinate_system = _normalized_coordinate_system(
        origin_object.get("coordinateSystem", default_coordinate_system)
        if isinstance(origin_object, dict)
        else default_coordinate_system
    )
    destination_coordinate_system = _normalized_coordinate_system(
        destination_object.get("coordinateSystem", default_coordinate_system)
        if isinstance(destination_object, dict)
        else default_coordinate_system
    )
    origin = _point_from_object(origin_object) or _flat_point(task, "start")
    destination = _point_from_object(destination_object) or _flat_point(task, "end")

    start_name = (
        (origin_object or {}).get("name") if isinstance(origin_object, dict) else None
    ) or task.get("startLocation")
    end_name = (
        (destination_object or {}).get("name") if isinstance(destination_object, dict) else None
    ) or task.get("endLocation")

    if origin is None:
        origin = geocode_amap(start_name, amap_key, city=city)
        origin_system = "WGS84"
    else:
        origin_system = origin_coordinate_system
    if destination is None:
        destination = geocode_amap(end_name, amap_key, city=city)
        destination_system = "WGS84"
    else:
        destination_system = destination_coordinate_system

    origin = _convert_point(origin, origin_system)
    destination = _convert_point(destination, destination_system)
    return {
        "task_id": task_id,
        "vehicle_id": vehicle_code,
        "start_name": start_name,
        "end_name": end_name,
        "waypoints": [
            {"lon": origin[0], "lat": origin[1]},
            {"lon": destination[0], "lat": destination[1]},
        ],
    }


def fetch_task_route_context(task_url, token=None, amap_key=None, city=None):
    headers = {"Accept": "application/json", "User-Agent": "smart-logistics-demo/1.0"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(task_url, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except Exception as exc:
        raise RuntimeError(f"读取业务任务接口失败：{type(exc).__name__}") from exc
    return parse_task_route_context(payload, amap_key=amap_key, city=city)
