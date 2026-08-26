# -*- coding: utf-8 -*-
"""Normalize ETA planned-route points to WGS84."""

import math


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
    """Convert WGS84 to GCJ-02; retained for coordinate-contract tests."""
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
    """Convert a backend-owned GCJ-02 route point to WGS84 GPS."""
    if _outside_china(lon, lat):
        return lon, lat
    converted_lon, converted_lat = wgs84_to_gcj02(lon, lat)
    return lon * 2 - converted_lon, lat * 2 - converted_lat


def parse_route_points(route_points, coordinate_system):
    """Return ``(lat, lon)`` WGS84 points from planned-route ``points``."""
    normalized_system = str(coordinate_system or "").upper().replace("-", "")
    if normalized_system not in ("GCJ02", "WGS84"):
        raise ValueError("route.coordinateSystem必须为GCJ02或WGS84")
    if not isinstance(route_points, list):
        raise ValueError("planned-route.points必须是数组")

    points = []
    for index, item in enumerate(route_points):
        try:
            if isinstance(item, dict):
                lon = float(item["longitude"])
                lat = float(item["latitude"])
            elif isinstance(item, (list, tuple)) and len(item) == 2:
                lon = float(item[0])
                lat = float(item[1])
            else:
                raise TypeError
        except (KeyError, TypeError, ValueError, IndexError) as exc:
            raise ValueError(
                f"第{index + 1}个路线点必须是[longitude, latitude]或包含有效longitude和latitude的对象"
            ) from exc
        if not -180 <= lon <= 180 or not -90 <= lat <= 90:
            raise ValueError(f"第{index + 1}个路线点超出经纬度范围")
        if normalized_system == "GCJ02":
            lon, lat = gcj02_to_wgs84(lon, lat)
        point = (lat, lon)
        if not points or point != points[-1]:
            points.append(point)

    if len(points) < 2:
        raise ValueError("planned-route.points至少需要两个不同的路线点")
    return points
