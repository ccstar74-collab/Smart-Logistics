package com.smart_logistics.backend.service;

import com.smart_logistics.backend.mapper.TransportTaskRoutePointsTypeHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransportTaskRoutePointsTypeHandlerTest {

    private final TransportTaskRoutePointsTypeHandler typeHandler =
            new TransportTaskRoutePointsTypeHandler();

    @Test
    void preservesPointOrderAndDoublePrecisionAcrossJsonRoundTrip() throws Exception {
        List<List<Double>> points = List.of(
                List.of(106.570123456789, 29.490987654321),
                List.of(106.580000000001, 29.500000000009),
                List.of(106.610987654321, 29.520123456789));

        String json = typeHandler.serialize(points);
        List<List<Double>> restored = typeHandler.deserialize(json);

        assertEquals(points, restored);
        assertEquals(List.of(106.570123456789, 29.490987654321), restored.getFirst());
        assertEquals(List.of(106.610987654321, 29.520123456789), restored.getLast());
    }

    @Test
    void rejectsCoordinatesOutsideLongitudeLatitudeRanges() {
        assertThrows(java.sql.SQLException.class, () -> typeHandler.deserialize(
                "[[29.49,206.57],[29.50,206.61]]"));
    }
}
