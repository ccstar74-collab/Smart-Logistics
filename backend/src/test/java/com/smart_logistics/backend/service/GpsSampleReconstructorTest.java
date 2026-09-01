package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.realtime.GpsFieldRecord;
import com.smart_logistics.backend.dto.realtime.GpsSample;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpsSampleReconstructorTest {
    private final GpsSampleReconstructor reconstructor =
            new GpsSampleReconstructor(Duration.ofMillis(500));
    private final Instant base = Instant.parse("2026-08-25T02:00:00Z");

    @Test
    void reconstructsFieldsWhoseTimestampsDifferByMilliseconds() {
        List<GpsSample> result = reconstructor.reconstruct(List.of(
                row("sim_019", "lat", 31.20, 0),
                row("sim_019", "lon", 121.50, 12),
                row("sim_019", "speed", 42.5, 25),
                row("sim_019", "direction", 90, 31)));

        assertEquals(1, result.size());
        assertEquals(121.50, result.getFirst().longitude());
        assertEquals(31.20, result.getFirst().latitude());
        assertEquals(42.5, result.getFirst().speed());
        assertEquals(90, result.getFirst().direction());
        assertEquals(base.plusMillis(31), result.getFirst().collectedAt());
    }

    @Test
    void mapsCanonicalInfluxFieldNamesToApiSampleFieldsRegardlessOfOrder() {
        List<GpsSample> result = reconstructor.reconstruct(List.of(
                row("sim_000", "heading", 90, 31),
                row("sim_000", "longitude", 121.50, 12),
                row("sim_000", "speed_kmh", 42.5, 25),
                row("sim_000", "latitude", 31.20, 0)));

        assertEquals(1, result.size());
        GpsSample sample = result.getFirst();
        assertEquals(31.20, sample.latitude());
        assertEquals(121.50, sample.longitude());
        assertEquals(42.5, sample.speed());
        assertEquals(90, sample.direction());
    }

    @Test
    void dropsCandidateMissingLongitude() {
        assertEquals(List.of(), reconstructor.reconstruct(List.of(
                row("sim_019", "lat", 31.20, 0),
                row("sim_019", "speed", 20, 10))));
    }

    @Test
    void dropsCandidateMissingLatitude() {
        assertEquals(List.of(), reconstructor.reconstruct(List.of(
                row("sim_019", "lon", 121.50, 0),
                row("sim_019", "direction", 180, 10))));
    }

    @Test
    void sortsOutOfOrderRowsAndReconstructsConsecutiveSamples() {
        List<GpsSample> result = reconstructor.reconstruct(List.of(
                row("sim_019", "lon", 121.60, 2020),
                row("sim_019", "lat", 31.20, 0),
                row("sim_019", "lat", 31.30, 2000),
                row("sim_019", "lon", 121.50, 20)));

        assertEquals(2, result.size());
        assertEquals(121.50, result.get(0).longitude());
        assertEquals(121.60, result.get(1).longitude());
        assertEquals(0.0, result.get(0).speed());
        assertEquals(0.0, result.get(0).direction());
    }

    @Test
    void suppliesFrontendSafeDefaultsAndNormalizesNorthHeading() {
        List<GpsSample> result = reconstructor.reconstruct(List.of(
                row("sim_999", "lat", 29.61, 0),
                row("sim_999", "lon", 106.50, 0),
                row("sim_999", "heading", 360.0, 0)));

        assertEquals(1, result.size());
        assertEquals(0.0, result.getFirst().speed());
        assertEquals(0.0, result.getFirst().direction());
    }

    @Test
    void neverPairsRowsAcrossVehiclesOrOutsideTolerance() {
        List<GpsSample> result = reconstructor.reconstruct(List.of(
                row("sim_019", "lat", 31.20, 0),
                row("sim_020", "lon", 120.10, 10),
                row("sim_019", "lon", 121.50, 800),
                row("sim_020", "lat", 30.10, 20)));

        assertEquals(1, result.size());
        assertEquals("sim_020", result.getFirst().vehicleId());
    }

    private GpsFieldRecord row(String vehicleId, String field, double value, long millis) {
        return new GpsFieldRecord(vehicleId, field, value, base.plusMillis(millis));
    }
}
