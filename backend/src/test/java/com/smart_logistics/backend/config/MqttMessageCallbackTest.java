package com.smart_logistics.backend.config;

import com.smart_logistics.backend.dto.response.VehicleTraceWsDTO;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.handler.GpsWebSocketHandler;
import com.smart_logistics.backend.service.GpsInfluxService;
import com.smart_logistics.backend.service.VehicleService;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttMessageCallbackTest {
    @Mock
    private GpsWebSocketHandler gpsWebSocketHandler;
    @Mock
    private VehicleService vehicleService;
    @Mock
    private GpsInfluxService gpsInfluxService;

    @Test
    void normalizesDevicePayloadForFrontendWithoutDuplicatingInfluxWrite() throws Exception {
        MqttMessageCallback callback = new MqttMessageCallback();
        ReflectionTestUtils.setField(callback, "gpsWebSocketHandler", gpsWebSocketHandler);
        ReflectionTestUtils.setField(callback, "vehicleService", vehicleService);
        ReflectionTestUtils.setField(callback, "gpsInfluxService", gpsInfluxService);
        ReflectionTestUtils.setField(callback, "mqttWriteInflux", false);

        Vehicle vehicle = new Vehicle();
        vehicle.setId(6L);
        vehicle.setSimCode("sim_999");
        when(vehicleService.getVehicleBySimCode("sim_999")).thenReturn(vehicle);

        String json = """
                {"schema_version":"1.0","vehicle_id":"sim_999",
                 "timestamp":"2026-09-01T03:25:14.039Z",
                 "lat":29.618176,"lon":106.504800,
                 "speed_kmh":4.2,"heading":360.0,
                 "transport_status":"运输中","coordinate_system":"WGS84"}
                """;
        callback.messageArrived("iot/carla/vehicle/sim_999/gps",
                new MqttMessage(json.getBytes(StandardCharsets.UTF_8)));

        ArgumentCaptor<VehicleTraceWsDTO> captor =
                ArgumentCaptor.forClass(VehicleTraceWsDTO.class);
        verify(gpsWebSocketHandler).broadcastGps(captor.capture());
        VehicleTraceWsDTO message = captor.getValue();
        assertEquals("6", message.getVehicleId());
        assertEquals("sim_999", message.getSimCode());
        assertEquals(106.504800, message.getLongitude());
        assertEquals(29.618176, message.getLatitude());
        assertEquals(4.2, message.getSpeed());
        assertEquals(0.0, message.getDirection());
        assertEquals("WGS84", message.getCoordinateSystem());
        verify(gpsInfluxService, never()).writeGpsPoint(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyLong());
    }
}
