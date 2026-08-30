package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.TrafficSnapshot;
import com.smart_logistics.backend.mapper.TrafficSnapshotTypeHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrafficSnapshotTypeHandlerTest {

    private final TrafficSnapshotTypeHandler typeHandler =
            new TrafficSnapshotTypeHandler();

    @Test
    void preservesTrafficFactsAcrossJsonRoundTrip() throws Exception {
        TrafficSnapshot snapshot = new TrafficSnapshot(
                "AMAP_DRIVING_V3", "躲避拥堵", false, 7,
                120, 8_000, 600, 300, 40);

        assertEquals(snapshot, typeHandler.deserialize(typeHandler.serialize(snapshot)));
        assertEquals(9_060, snapshot.observedDistanceMeters());
    }

    @Test
    void keepsHistoricalNullSnapshotNullable() throws Exception {
        assertNull(typeHandler.deserialize(null));
        assertNull(typeHandler.deserialize(""));
    }

    @Test
    void rejectsNegativeTrafficValuesFromCorruptJson() {
        assertThrows(java.sql.SQLException.class, () -> typeHandler.deserialize("""
                {"source":"AMAP_DRIVING_V3","strategy":"fast",
                 "restriction":false,"trafficLights":-1,
                 "unknownDistanceMeters":0,"smoothDistanceMeters":1,
                 "slowDistanceMeters":0,"congestedDistanceMeters":0,
                 "severeCongestedDistanceMeters":0}
                """));
    }
}
