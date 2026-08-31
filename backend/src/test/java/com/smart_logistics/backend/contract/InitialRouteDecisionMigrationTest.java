package com.smart_logistics.backend.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InitialRouteDecisionMigrationTest {

    @Test
    void migrationDefinesDecisionCandidateSnapshotsAndConcurrencyGuards()
            throws Exception {
        String sql = Files.readString(Path.of("..", "docs", "sql",
                "016_initial_route_decision.sql"));
        String normalized = sql.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        assertTrue(normalized.contains("create table initial_route_decision"));
        assertTrue(normalized.contains("create table initial_route_candidate"));
        assertTrue(normalized.contains("unique key uk_initial_route_decision_idempotency"
                + " (idempotency_key)"));
        assertTrue(normalized.contains("unique key uk_initial_route_decision_confirmation_key"
                + " (confirmation_idempotency_key)"));
        assertTrue(normalized.contains("unique key uk_initial_route_decision_task (task_id)"));
        assertTrue(normalized.contains("unique key uk_initial_route_candidate_route"
                + " (decision_id, preview_route_id)"));
        assertTrue(normalized.contains("start_snapshot longtext not null"));
        assertTrue(normalized.contains("destination_snapshot longtext not null"));
        assertTrue(normalized.contains("traffic_snapshot longtext not null"));
        assertTrue(normalized.contains("weather_snapshot longtext not null"));
        assertTrue(normalized.contains("points longtext not null"));
        assertTrue(normalized.contains("foreign key (decision_id)"
                + " references initial_route_decision (decision_id)"));
    }
}
