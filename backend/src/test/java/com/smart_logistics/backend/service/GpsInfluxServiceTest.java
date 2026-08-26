package com.smart_logistics.backend.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.smart_logistics.backend.dto.realtime.GpsSample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GpsInfluxServiceTest {

    @Mock private InfluxDBClient influxDBClient;
    @Mock private QueryApi queryApi;
    @Mock private WriteApiBlocking writeApi;
    @Mock private FluxTable table;

    private GpsInfluxService service;

    @BeforeEach
    void setUp() {
        service = new GpsInfluxService();
        ReflectionTestUtils.setField(service, "influxDBClient", influxDBClient);
        ReflectionTestUtils.setField(service, "bucket", "gps-bucket");
        ReflectionTestUtils.setField(service, "gpsSampleReconstructor",
                new GpsSampleReconstructor(Duration.ofSeconds(1)));
    }

    @Test
    void queriesCanonicalVehicleGpsSchemaAndReconstructsCompleteSample() {
        Instant collectedAt = Instant.parse("2026-08-26T06:00:00Z");
        List<FluxRecord> records = List.of(
                record("sim_001", "heading", 92.0, collectedAt),
                record("sim_001", "longitude", 106.735012, collectedAt),
                record("sim_001", "speed_kmh", 36.5, collectedAt),
                record("sim_001", "latitude", 29.610634, collectedAt));
        when(influxDBClient.getQueryApi()).thenReturn(queryApi);
        when(queryApi.query(anyString())).thenReturn(List.of(table));
        when(table.getRecords()).thenReturn(records);

        List<GpsSample> samples = service.querySamples(List.of("sim_001"),
                collectedAt.minusSeconds(10), collectedAt.plusSeconds(10));

        assertEquals(1, samples.size());
        GpsSample sample = samples.getFirst();
        assertEquals("sim_001", sample.vehicleId());
        assertEquals(106.735012, sample.longitude());
        assertEquals(29.610634, sample.latitude());
        assertEquals(36.5, sample.speed());
        assertEquals(92.0, sample.direction());
        assertEquals(collectedAt, sample.collectedAt());

        ArgumentCaptor<String> fluxCaptor = ArgumentCaptor.forClass(String.class);
        verify(queryApi).query(fluxCaptor.capture());
        String flux = fluxCaptor.getValue();
        assertTrue(flux.contains("r._measurement == \"vehicle_gps\""));
        assertTrue(flux.contains("r.vehicle_id"));
        assertTrue(flux.contains("\"latitude\",\"longitude\",\"speed_kmh\",\"heading\""));
    }

    @Test
    void productionWriterUsesCanonicalMeasurementTagAndFields() {
        when(influxDBClient.getWriteApiBlocking()).thenReturn(writeApi);

        service.writeGpsPoint("sim_001", "29.610634", "106.735012",
                36.5, 92.0, 1_777_000_000_000L);

        ArgumentCaptor<String> lineCaptor = ArgumentCaptor.forClass(String.class);
        verify(writeApi).writeRecord(eq(WritePrecision.NS), lineCaptor.capture());
        String line = lineCaptor.getValue();
        assertTrue(line.startsWith("vehicle_gps,vehicle_id=sim_001 "));
        assertTrue(line.contains("longitude=106.735012"));
        assertTrue(line.contains("latitude=29.610634"));
        assertTrue(line.contains("speed_kmh=36.500000"));
        assertTrue(line.contains("heading=92.000000"));
        assertFalse(line.contains("gps_track"));
        assertFalse(line.contains("vehicleId="));
    }

    private FluxRecord record(String vehicleId, String field, double value, Instant time) {
        FluxRecord record = mock(FluxRecord.class);
        when(record.getValueByKey("vehicle_id")).thenReturn(vehicleId);
        when(record.getField()).thenReturn(field);
        when(record.getValue()).thenReturn(value);
        when(record.getTime()).thenReturn(time);
        return record;
    }
}
