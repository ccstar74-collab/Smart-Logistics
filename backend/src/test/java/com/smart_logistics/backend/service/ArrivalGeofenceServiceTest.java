package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.response.ArrivalEligibilityResponse;
import com.smart_logistics.backend.dto.response.VehicleLocationResponse;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.enums.ArrivalEligibilityReason;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.service.eta.Wgs84ToGcj02Converter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArrivalGeofenceServiceTest {

    private static final double GPS_LONGITUDE = 106.550713;
    private static final double GPS_LATITUDE = 29.608567;
    private static final OffsetDateTime COLLECTED_AT =
            OffsetDateTime.parse("2026-08-30T16:00:00+08:00");

    @Mock
    private VehicleLocationQueryService vehicleLocationQueryService;

    private ArrivalGeofenceService service;

    @BeforeEach
    void setUp() {
        service = new ArrivalGeofenceService(vehicleLocationQueryService, 200);
    }

    @Test
    void onlineVehicleAtDestinationCanComplete() {
        TransportTask task = transportingTaskAt(GPS_LONGITUDE, GPS_LATITUDE);
        when(vehicleLocationQueryService.getLatestLocation(20L))
                .thenReturn(location(true));

        ArrivalEligibilityResponse result = service.evaluate(task);

        assertTrue(result.eligible());
        assertEquals(ArrivalEligibilityReason.ARRIVAL_ALLOWED, result.reason());
        assertEquals(0.0, result.distanceMeters(), 0.1);
        assertEquals(200.0, result.radiusMeters());
        assertEquals(COLLECTED_AT, result.latestLocationAt());
    }

    @Test
    void onlineVehicleOutsideFenceCannotComplete() {
        TransportTask task = transportingTaskAt(GPS_LONGITUDE + 0.01, GPS_LATITUDE);
        when(vehicleLocationQueryService.getLatestLocation(20L))
                .thenReturn(location(true));

        ArrivalEligibilityResponse result = service.evaluate(task);

        assertFalse(result.eligible());
        assertEquals(ArrivalEligibilityReason.OUTSIDE_GEOFENCE, result.reason());
        assertTrue(result.distanceMeters() > 900);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireArrivalAllowed(task));
        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void staleVehicleLocationIsReportedAsOffline() {
        TransportTask task = transportingTaskAt(GPS_LONGITUDE, GPS_LATITUDE);
        when(vehicleLocationQueryService.getLatestLocation(20L))
                .thenReturn(location(false));

        ArrivalEligibilityResponse result = service.evaluate(task);

        assertFalse(result.eligible());
        assertEquals(ArrivalEligibilityReason.LOCATION_OFFLINE, result.reason());
        assertNull(result.distanceMeters());
    }

    @Test
    void missingGpsProducesStableIneligibleResponse() {
        TransportTask task = transportingTaskAt(GPS_LONGITUDE, GPS_LATITUDE);
        when(vehicleLocationQueryService.getLatestLocation(20L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "vehicle location not found"));

        ArrivalEligibilityResponse result = service.evaluate(task);

        assertFalse(result.eligible());
        assertEquals(ArrivalEligibilityReason.LOCATION_NOT_FOUND, result.reason());
        assertNull(result.latestLocationAt());
    }

    @Test
    void waitingTaskDoesNotQueryGps() {
        TransportTask task = transportingTaskAt(GPS_LONGITUDE, GPS_LATITUDE);
        task.setStatus(TransportTaskStatus.WAITING.name());

        ArrivalEligibilityResponse result = service.evaluate(task);

        assertEquals(ArrivalEligibilityReason.TASK_NOT_TRANSPORTING, result.reason());
        verify(vehicleLocationQueryService, never()).getLatestLocation(20L);
    }

    private TransportTask transportingTaskAt(double destinationWgsLongitude,
                                              double destinationWgsLatitude) {
        Wgs84ToGcj02Converter.Coordinate destination =
                Wgs84ToGcj02Converter.convert(
                        destinationWgsLongitude, destinationWgsLatitude);
        TransportTask task = new TransportTask();
        task.setId(1L);
        task.setVehicleId(20L);
        task.setStatus(TransportTaskStatus.TRANSPORTING.name());
        task.setEndLongitude(destination.longitude());
        task.setEndLatitude(destination.latitude());
        return task;
    }

    private VehicleLocationResponse location(boolean online) {
        return new VehicleLocationResponse(20L, "渝A00001",
                GPS_LONGITUDE, GPS_LATITUDE, 0.0, 0.0,
                COLLECTED_AT, online, 1L);
    }
}
