package com.smart_logistics.backend.service.eta;

import org.junit.jupiter.api.Test;

import java.time.Duration;

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
    void convertsWgs84CoordinateForDomesticRouteRequest() {
        Wgs84ToGcj02Converter.Coordinate converted =
                Wgs84ToGcj02Converter.convert(106.5712345, 29.4934567);

        assertTrue(Math.abs(converted.longitude() - 106.5712345) > 0.001);
        assertTrue(Math.abs(converted.latitude() - 29.4934567) > 0.001);
    }

    @Test
    void leavesOverseasCoordinateUnchanged() {
        Wgs84ToGcj02Converter.Coordinate converted =
                Wgs84ToGcj02Converter.convert(-0.1276, 51.5072);

        assertEquals(-0.1276, converted.longitude());
        assertEquals(51.5072, converted.latitude());
    }
}
