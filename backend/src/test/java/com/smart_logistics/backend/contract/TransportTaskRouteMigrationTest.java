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
}
