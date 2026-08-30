package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.dto.RealTimeGpsDTO;
import com.smart_logistics.backend.dto.VehicleTracePointDTO;
import com.smart_logistics.backend.dto.response.VehicleLocationResponse;
import com.smart_logistics.backend.service.VehicleLocationQueryService;
import com.smart_logistics.backend.service.VehicleService;
import com.smart_logistics.backend.service.VehicleTraceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VehicleLatestLocationMappingTest {

    @Mock private VehicleService vehicleService;
    @Mock private VehicleLocationQueryService vehicleLocationQueryService;
    @Mock private VehicleTraceService vehicleTraceService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        VehicleController vehicleController = new VehicleController(
                vehicleService, vehicleLocationQueryService);
        VehicleTraceController vehicleTraceController = new VehicleTraceController();
        ReflectionTestUtils.setField(
                vehicleTraceController, "vehicleTraceService", vehicleTraceService);
        mockMvc = MockMvcBuilders.standaloneSetup(
                vehicleController, vehicleTraceController).build();
    }

    @Test
    void numericLatestPathMapsOnlyToOfficialVehicleController() throws Exception {
        when(vehicleLocationQueryService.getLatestLocation(1L)).thenReturn(
                location(1L, "沪A10001"));

        mockMvc.perform(get("/api/v1/vehicles/1/location/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vehicleId").value(1))
                .andExpect(jsonPath("$.data.plateNumber").value("沪A10001"));

        verify(vehicleLocationQueryService).getLatestLocation(1L);
        verify(vehicleTraceService, never()).getVehicleLatestPoint("1");
    }

    @Test
    void simCodeLatestPathMapsOnlyToVehicleTraceController() throws Exception {
        RealTimeGpsDTO latest = new RealTimeGpsDTO();
        latest.setLon(121.6);
        latest.setLat(31.3);
        latest.setSpeed(35.0);
        latest.setHeading(80.0);
        latest.setTimestamp(OffsetDateTime.parse("2026-08-30T10:00:00+08:00")
                .toInstant().toEpochMilli());
        when(vehicleTraceService.getVehicleLatestPoint("sim_001")).thenReturn(latest);

        mockMvc.perform(get(
                        "/api/v1/vehicles/by-sim-code/sim_001/location/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value("sim_001"))
                .andExpect(jsonPath("$.longitude").value(121.6));

        verify(vehicleTraceService).getVehicleLatestPoint("sim_001");
        verifyNoInteractions(vehicleLocationQueryService);
    }

    @Test
    void batchLatestPathRemainsMappedToVehicleController() throws Exception {
        when(vehicleLocationQueryService.getLatestLocations()).thenReturn(
                List.of(location(1L, "沪A10001")));

        mockMvc.perform(get("/api/v1/vehicles/locations/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].vehicleId").value(1));

        verify(vehicleLocationQueryService).getLatestLocations();
        verifyNoInteractions(vehicleTraceService);
    }

    @Test
    void numericHistoryPathMapsOnlyToOfficialVehicleController() throws Exception {
        OffsetDateTime start = OffsetDateTime.parse("2026-08-30T10:00:00+08:00");
        OffsetDateTime end = OffsetDateTime.parse("2026-08-30T11:00:00+08:00");
        when(vehicleLocationQueryService.getLocationHistory(1L, start, end)).thenReturn(
                List.of(location(1L, "沪A10001")));

        mockMvc.perform(get(URI.create(
                        "/api/v1/vehicles/1/location-history"
                                + "?startTime=2026-08-30T10:00:00%2B08:00"
                                + "&endTime=2026-08-30T11:00:00%2B08:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].vehicleId").value(1))
                .andExpect(jsonPath("$.data[0].plateNumber").value("沪A10001"));

        verify(vehicleLocationQueryService).getLocationHistory(1L, start, end);
        verifyNoInteractions(vehicleTraceService);
    }

    @Test
    void simCodeHistoryPathMapsOnlyToVehicleTraceController() throws Exception {
        long start = 1788055200000L;
        long end = 1788058800000L;
        VehicleTracePointDTO point = new VehicleTracePointDTO();
        point.setLon(121.6);
        point.setLat(31.3);
        point.setSpeed(35.0);
        point.setHeading(80.0);
        point.setTimestamp(start);
        when(vehicleTraceService.getVehicleTrace("sim_001", start, end)).thenReturn(
                List.of(point));

        mockMvc.perform(get(
                        "/api/v1/vehicles/by-sim-code/sim_001/location-history")
                        .param("start", Long.toString(start))
                        .param("end", Long.toString(end)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleId").value("sim_001"))
                .andExpect(jsonPath("$[0].longitude").value(121.6));

        verify(vehicleTraceService).getVehicleTrace("sim_001", start, end);
        verifyNoInteractions(vehicleLocationQueryService);
    }

    private VehicleLocationResponse location(Long vehicleId, String plateNumber) {
        return new VehicleLocationResponse(vehicleId, plateNumber, 121.5, 31.2,
                40.0, 90.0,
                OffsetDateTime.parse("2026-08-30T10:00:00+08:00"),
                true, 10L);
    }
}
