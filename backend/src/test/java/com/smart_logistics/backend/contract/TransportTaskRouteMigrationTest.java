package com.smart_logistics.backend.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TransportTaskRouteMigrationTest {

    @Test
    void migrationDefinesStableIdVersionAndActiveLookupConstraints() throws Exception {
        String migration = Files.readString(Path.of("..", "docs", "sql",
                "009_transport_task_route.sql"));

        assertTrue(migration.contains("UNIQUE (route_id)"));
        assertTrue(migration.contains("UNIQUE (task_id, route_version)"));
        assertTrue(migration.contains("(task_id, status)"));
        assertTrue(migration.contains("FOREIGN KEY (task_id) REFERENCES transport_task(id)"));
        assertTrue(migration.contains("READY / ACTIVE / INACTIVE"));
    }

    @Test
    void activationMigrationAddsNullablePlaybackIntervalWithoutFakeBackfill()
            throws Exception {
        String migration = Files.readString(Path.of("..", "docs", "sql",
                "013_transport_task_route_activation.sql"));

        assertTrue(migration.contains("activated_at DATETIME(3) NULL"));
        assertTrue(migration.contains("deactivated_at DATETIME(3) NULL"));
        assertTrue(migration.contains("(task_id, activated_at, route_version)"));
        assertTrue(!migration.toUpperCase().contains("UPDATE TRANSPORT_TASK_ROUTE"));
    }

    @Test
    void trafficMigrationAddsNullableSourceAwareSnapshotWithoutFakeBackfill()
            throws Exception {
        String migration = Files.readString(Path.of("..", "docs", "sql",
                "015_route_traffic_snapshot.sql"));

        assertTrue(migration.contains("traffic_snapshot LONGTEXT NULL"));
        assertTrue(migration.contains("source, strategy, restriction and TMC distances"));
        assertTrue(!migration.toUpperCase().contains("UPDATE TRANSPORT_TASK_ROUTE"));
    }
}
