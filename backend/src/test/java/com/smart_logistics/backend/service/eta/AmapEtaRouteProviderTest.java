package com.smart_logistics.backend.service.eta;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmapEtaRouteProviderTest {

    private final AmapEtaRouteProvider provider = new AmapEtaRouteProvider(
            "https://example.invalid/route", "test-key", Duration.ofSeconds(1));

    @Test
    void parsesDistanceDurationAndPolylineFromAmapResponse() {
        EtaPlannedRoute route = provider.parseResponse("""
                {
                  "status": "1",
                  "info": "OK",
                  "infocode": "10000",
                  "route": {"paths": [{
                    "distance": "3852",
                    "duration": "642",
                    "steps": [
                      {"polyline": "106.570000,29.490000;106.580000,29.500000"},
                      {"polyline": "106.580000,29.500000;106.610000,29.520000"}
                    ]
                  }]}
                }
                """);

        assertEquals(3852, route.distanceMeters());
        assertEquals(Duration.ofSeconds(642), route.referenceDuration());
        assertEquals(3, route.polyline().size());
    }

    @Test
    void rejectsProviderErrorResponse() {
        EtaProviderException exception = assertThrows(EtaProviderException.class,
                () -> provider.parseResponse("""
                        {"status":"0","info":"INVALID_USER_KEY","infocode":"10001"}
                        """));

        assertTrue(exception.getMessage().contains("INVALID_USER_KEY"));
    }

    @Test
    void sendsStoredGcj02CoordinatesWithoutSecondConversion() {
        String uri = provider.buildUri(
                106.571234, 29.493456, 106.612345, 29.523456).toString();

        assertTrue(uri.contains("origin=106.571234,29.493456"));
        assertTrue(uri.contains("destination=106.612345,29.523456"));
    }

    @Test
    void requestsAmapMultiStrategyForRouteCandidates() {
        String uri = provider.buildCandidateUri(
                106.571234, 29.493456, 106.612345, 29.523456).toString();

        assertTrue(uri.contains("strategy=11"));
        assertTrue(uri.contains("extensions=all"));
    }

    @Test
    void parsesEveryCandidatePathFromAmapResponse() {
        List<EtaPlannedRoute> routes = provider.parseCandidateResponse("""
                {
                  "status": "1",
                  "info": "OK",
                  "infocode": "10000",
                  "route": {"paths": [
                    {
                      "distance": "3800", "duration": "600",
                      "steps": [{"polyline":
                        "106.570000,29.490000;106.580000,29.500000"}]
                    },
                    {
                      "distance": "3500", "duration": "680",
                      "steps": [{"polyline":
                        "106.570000,29.490000;106.590000,29.510000"}]
                    },
                    {
                      "distance": "4100", "duration": "570",
                      "steps": [{"polyline":
                        "106.570000,29.490000;106.600000,29.520000"}]
                    }
                  ]}
                }
                """);

        assertEquals(3, routes.size());
        assertEquals(3500, routes.get(1).distanceMeters());
        assertEquals(Duration.ofSeconds(570), routes.get(2).referenceDuration());
    }

    @Test
    void standardizesTrafficFactsFromEveryTmcSegment() {
        EtaPlannedRoute route = provider.parseResponse("""
                {
                  "status":"1","route":{"paths":[{
                    "distance":"1000","duration":"120",
                    "strategy":"躲避拥堵","restriction":"1",
                    "traffic_lights":"4",
                    "steps":[{
                      "polyline":"106.57,29.49;106.58,29.50",
                      "tmcs":[
                        {"status":"畅通","distance":"600"},
                        {"status":"缓行","distance":"200"},
                        {"status":"拥堵","distance":"100"},
                        {"status":"严重拥堵","distance":"50"},
                        {"status":"未知","distance":"50"}
                      ]
                    }]
                  }]}
                }
                """);

        assertEquals("AMAP_DRIVING_V3", route.trafficSnapshot().source());
        assertEquals("躲避拥堵", route.trafficSnapshot().strategy());
        assertTrue(route.trafficSnapshot().restriction());
        assertEquals(4, route.trafficSnapshot().trafficLights());
        assertEquals(600, route.trafficSnapshot().smoothDistanceMeters());
        assertEquals(200, route.trafficSnapshot().slowDistanceMeters());
        assertEquals(100, route.trafficSnapshot().congestedDistanceMeters());
        assertEquals(50, route.trafficSnapshot().severeCongestedDistanceMeters());
        assertEquals(50, route.trafficSnapshot().unknownDistanceMeters());
    }

    @Test
    void leavesOverseasCoordinateUnchanged() {
        Wgs84ToGcj02Converter.Coordinate converted =
                Wgs84ToGcj02Converter.convert(-0.1276, 51.5072);

        assertEquals(-0.1276, converted.longitude());
        assertEquals(51.5072, converted.latitude());
    }
}
