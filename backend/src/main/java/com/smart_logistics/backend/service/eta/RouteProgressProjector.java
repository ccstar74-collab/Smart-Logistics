package com.smart_logistics.backend.service.eta;

import java.util.List;

public class RouteProgressProjector {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    public EtaRouteProgress project(EtaCoordinate current,
                                    List<EtaCoordinate> polyline) {
        if (polyline.size() < 2) {
            throw new IllegalArgumentException("polyline must contain at least two points");
        }
        double[] segmentLengths = new double[polyline.size() - 1];
        double totalLength = 0;
        for (int index = 0; index < segmentLengths.length; index++) {
            segmentLengths[index] = distance(polyline.get(index), polyline.get(index + 1));
            totalLength += segmentLengths[index];
        }

        double nearestDistance = Double.MAX_VALUE;
        double distanceAlongRoute = 0;
        double completedBeforeSegment = 0;
        for (int index = 0; index < segmentLengths.length; index++) {
            Projection projection = projectOnSegment(
                    current, polyline.get(index), polyline.get(index + 1));
            if (projection.distanceMeters() < nearestDistance) {
                nearestDistance = projection.distanceMeters();
                distanceAlongRoute = completedBeforeSegment
                        + projection.fraction() * segmentLengths[index];
            }
            completedBeforeSegment += segmentLengths[index];
        }
        double remainingAlongRoute = Math.max(0, totalLength - distanceAlongRoute);
        return new EtaRouteProgress(remainingAlongRoute + nearestDistance, nearestDistance);
    }

    private Projection projectOnSegment(EtaCoordinate point,
                                        EtaCoordinate start,
                                        EtaCoordinate end) {
        double referenceLatitude = Math.toRadians(point.latitude());
        double longitudeScale = 111_320.0 * Math.cos(referenceLatitude);
        double latitudeScale = 110_540.0;
        double startX = (start.longitude() - point.longitude()) * longitudeScale;
        double startY = (start.latitude() - point.latitude()) * latitudeScale;
        double endX = (end.longitude() - point.longitude()) * longitudeScale;
        double endY = (end.latitude() - point.latitude()) * latitudeScale;
        double segmentX = endX - startX;
        double segmentY = endY - startY;
        double lengthSquared = segmentX * segmentX + segmentY * segmentY;
        double fraction = lengthSquared == 0 ? 0
                : -(startX * segmentX + startY * segmentY) / lengthSquared;
        fraction = Math.max(0, Math.min(1, fraction));
        double projectedX = startX + fraction * segmentX;
        double projectedY = startY + fraction * segmentY;
        return new Projection(fraction, Math.hypot(projectedX, projectedY));
    }

    private double distance(EtaCoordinate first, EtaCoordinate second) {
        double latitudeDelta = Math.toRadians(second.latitude() - first.latitude());
        double longitudeDelta = Math.toRadians(second.longitude() - first.longitude());
        double firstLatitude = Math.toRadians(first.latitude());
        double secondLatitude = Math.toRadians(second.latitude());
        double value = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(firstLatitude) * Math.cos(secondLatitude)
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
    }

    private record Projection(double fraction, double distanceMeters) {
    }
}
