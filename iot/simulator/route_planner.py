# -*- coding: utf-8 -*-
"""Road route providers and coordinate conversion helpers.

The simulator publishes GPS in WGS84. AMap's domestic Web Service APIs use
GCJ-02 coordinates, so AMap inputs and outputs are converted at this boundary.
"""

import json
import math
import urllib.parse
import urllib.request


AMAP_DRIVING_URL = "https://restapi.amap.com/v5/direction/driving"
AMAP_GEOCODE_URL = "https://restapi.amap.com/v3/geocode/geo"


def parse_route_coordinates(route_text):
    coordinates = []
    try:
        for item in route_text.split(";"):
            lon, lat = (float(value.strip()) for value in item.split(","))
            if not -180 <= lon <= 180 or not -90 <= lat <= 90:
                raise ValueError("coordinate out of range")
            coordinates.append((lon, lat))
    except (AttributeError, ValueError) as exc:
        raise ValueError("路线格式应为 经度,纬度;经度,纬度") from exc
    if len(coordinates) < 2:
        raise ValueError("路线至少需要起点和终点")
    if len(coordinates) > 18:
        raise ValueError("高德驾车规划最多支持起点、终点和16个途经点")
    return coordinates


def _outside_china(lon, lat):
    return lon < 72.004 or lon > 137.8347 or lat < 0.8293 or lat > 55.8271


def _transform_lat(x, y):
    result = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y
    result += 0.1 * x * y + 0.2 * math.sqrt(abs(x))
    result += (20.0 * math.sin(6.0 * x * math.pi)
               + 20.0 * math.sin(2.0 * x * math.pi)) * 2.0 / 3.0
    result += (20.0 * math.sin(y * math.pi)
               + 40.0 * math.sin(y / 3.0 * math.pi)) * 2.0 / 3.0
    result += (160.0 * math.sin(y / 12.0 * math.pi)
               + 320.0 * math.sin(y * math.pi / 30.0)) * 2.0 / 3.0
    return result


def _transform_lon(x, y):
    result = 300.0 + x + 2.0 * y + 0.1 * x * x
    result += 0.1 * x * y + 0.1 * math.sqrt(abs(x))
    result += (20.0 * math.sin(6.0 * x * math.pi)
               + 20.0 * math.sin(2.0 * x * math.pi)) * 2.0 / 3.0
    result += (20.0 * math.sin(x * math.pi)
               + 40.0 * math.sin(x / 3.0 * math.pi)) * 2.0 / 3.0
    result += (150.0 * math.sin(x / 12.0 * math.pi)
               + 300.0 * math.sin(x / 30.0 * math.pi)) * 2.0 / 3.0
    return result


def wgs84_to_gcj02(lon, lat):
    """Convert WGS84 to GCJ-02 for a mainland China AMap request."""
    if _outside_china(lon, lat):
        return lon, lat
    latitude_delta = _transform_lat(lon - 105.0, lat - 35.0)
    longitude_delta = _transform_lon(lon - 105.0, lat - 35.0)
    rad_lat = lat / 180.0 * math.pi
    magic = math.sin(rad_lat)
    magic = 1 - 0.00669342162296594323 * magic * magic
    sqrt_magic = math.sqrt(magic)
    latitude_delta = (
        latitude_delta * 180.0
        / ((6335552.717000426 / (magic * sqrt_magic)) * math.pi)
    )
    longitude_delta = (
        longitude_delta * 180.0
        / ((6378245.0 / sqrt_magic) * math.cos(rad_lat) * math.pi)
    )
    return lon + longitude_delta, lat + latitude_delta


def gcj02_to_wgs84(lon, lat):
    """Convert GCJ-02 to WGS84 with sub-GPS-noise accuracy."""
    if _outside_china(lon, lat):
        return lon, lat
    converted_lon, converted_lat = wgs84_to_gcj02(lon, lat)
    return lon * 2 - converted_lon, lat * 2 - converted_lat


def _request_json(url, parameters, service_name):
    query = urllib.parse.urlencode(parameters)
    request = urllib.request.Request(
        f"{url}?{query}", headers={"User-Agent": "smart-logistics-demo/1.0"}
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.loads(response.read().decode("utf-8"))
    except Exception as exc:
        # Do not include request.get_full_url(): it contains the AMap key.
        raise RuntimeError(f"{service_name}请求失败：{type(exc).__name__}") from exc


def _round_trip(forward_points):
    # Avoid jumping from destination back to origin during a long-running demo.
    return forward_points + list(reversed(forward_points[1:-1]))


def load_amap_road_route(coordinates, amap_key, strategy=32):
    if not amap_key:
        raise ValueError("使用高德路线规划时必须设置 AMAP_WEB_SERVICE_KEY")

    gcj_coordinates = [wgs84_to_gcj02(lon, lat) for lon, lat in coordinates]
    parameters = {
        "key": amap_key,
        "origin": "%.6f,%.6f" % gcj_coordinates[0],
        "destination": "%.6f,%.6f" % gcj_coordinates[-1],
        "strategy": str(strategy),
        "show_fields": "polyline,cost",
        "output": "json",
    }
    if len(gcj_coordinates) > 2:
        parameters["waypoints"] = ";".join(
            "%.6f,%.6f" % item for item in gcj_coordinates[1:-1]
        )

    result = _request_json(AMAP_DRIVING_URL, parameters, "高德驾车路线规划")
    route = result.get("route") or {}
    paths = route.get("paths") or []
    if str(result.get("status")) != "1" or not paths:
        info = result.get("info") or result.get("infocode") or "unknown"
        raise RuntimeError(f"高德驾车路线规划失败：{info}")

    path = paths[0]
    forward_points = []
    for step in path.get("steps") or []:
        for item in (step.get("polyline") or "").split(";"):
            if not item:
                continue
            gcj_lon, gcj_lat = (float(value) for value in item.split(","))
            wgs_lon, wgs_lat = gcj02_to_wgs84(gcj_lon, gcj_lat)
            point = (wgs_lat, wgs_lon)
            if not forward_points or point != forward_points[-1]:
                forward_points.append(point)

    if len(forward_points) < 2:
        raise RuntimeError("高德驾车路线规划没有返回足够的polyline轨迹点")
    distance_m = float(path.get("distance") or 0)
    print(
        "[ROUTE][AMAP] 已加载高德真实道路：%.2f km，%d 个轨迹节点（GCJ-02已转WGS84，往返循环）"
        % (distance_m / 1000.0, len(_round_trip(forward_points)))
    )
    return _round_trip(forward_points)


def load_road_route(route_text, amap_key, strategy=32):
    """Load a road-snapped round trip from WGS84 lon/lat waypoints."""
    coordinates = parse_route_coordinates(route_text)
    return load_amap_road_route(coordinates, amap_key, strategy=strategy)


def geocode_amap(address, amap_key, city=None):
    """Resolve an address through AMap and return a WGS84 (lon, lat) pair."""
    if not address or not str(address).strip():
        raise ValueError("待解析地址不能为空")
    if not amap_key:
        raise ValueError("地址解析需要设置 AMAP_WEB_SERVICE_KEY")
    parameters = {"key": amap_key, "address": str(address).strip(), "output": "json"}
    if city:
        parameters["city"] = city
    result = _request_json(AMAP_GEOCODE_URL, parameters, "高德地理编码")
    geocodes = result.get("geocodes") or []
    if str(result.get("status")) != "1" or not geocodes:
        info = result.get("info") or result.get("infocode") or "unknown"
        raise RuntimeError(f"高德无法解析地址“{address}”：{info}")
    gcj_lon, gcj_lat = (float(value) for value in geocodes[0]["location"].split(","))
    return gcj02_to_wgs84(gcj_lon, gcj_lat)
