package com.smart_logistics.backend.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AlarmDispatchMigrationTest {

    @Test
    void migrationDefinesAlarmClosureAndNullableCommandLink() throws Exception {
        String migration = Files.readString(Path.of("..", "docs", "sql",
                "011_alarm_dispatch_resolution.sql"));
        assertTrue(migration.contains("vehicle_id BIGINT NULL"));
        assertTrue(migration.contains("condition_status VARCHAR(20) NOT NULL"));
        assertTrue(migration.contains("recovered_at DATETIME NULL"));
        assertTrue(migration.contains("resolution_remark VARCHAR(500) NULL"));
        assertTrue(migration.contains("alarm_id BIGINT NULL"));
        assertTrue(migration.contains("FOREIGN KEY (alarm_id) REFERENCES alarm(id)"));
        assertTrue(migration.contains("(alarm_id, status, id)"));
    }
}
