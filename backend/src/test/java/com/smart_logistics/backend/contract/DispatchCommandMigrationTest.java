package com.smart_logistics.backend.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchCommandMigrationTest {

    @Test
    void migrationDefinesWorkflowOwnershipAndInboxIndexes() throws Exception {
        String migration = Files.readString(Path.of("..", "docs", "sql",
                "010_dispatch_command_rest_workflow.sql"));
        assertTrue(migration.contains("target_driver_id BIGINT NOT NULL"));
        assertTrue(migration.contains("target_route_id VARCHAR(64) NULL"));
        assertTrue(migration.contains("FOREIGN KEY (target_route_id)"));
        assertTrue(migration.contains("(target_driver_id, status, created_at)"));
        assertTrue(migration.contains("acknowledged_at DATETIME NULL"));
        assertTrue(migration.contains("executing_at DATETIME NULL"));
    }
}
