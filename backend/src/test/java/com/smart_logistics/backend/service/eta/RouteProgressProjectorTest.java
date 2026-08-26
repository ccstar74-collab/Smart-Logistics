package com.smart_logistics.backend.service.eta;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteProgressProjectorTest {

    private final RouteProgressProjector projector = new RouteProgressProjector();
    private final List<EtaCoordinate> route = List.of(
            new EtaCoordinate(106.0000, 29.0000),
            new EtaCoordinate(106.0100, 29.0000),
            new EtaCoordinate(106.0200, 29.0000));

    @Test
    void projectsCurrentPointToRouteAndReturnsRemainingPolylineDistance() {
        EtaRouteProgress progress = projector.project(
                new EtaCoordinate(106.0100, 29.0001), route);

        assertTrue(progress.distanceFromRouteMeters() > 5);
        assertTrue(progress.distanceFromRouteMeters() < 20);
        assertTrue(progress.remainingDistanceMeters() > 950);
        assertTrue(progress.remainingDistanceMeters() < 1_000);
    }

    @Test
    void addsOffRouteDistanceToRemainingRouteDistance() {
        EtaRouteProgress onRoute = projector.project(
                new EtaCoordinate(106.0100, 29.0000), route);
        EtaRouteProgress offRoute = projector.project(
                new EtaCoordinate(106.0100, 29.0010), route);

        assertTrue(offRoute.remainingDistanceMeters() > onRoute.remainingDistanceMeters());
        assertTrue(offRoute.distanceFromRouteMeters() > 100);
    }
}
