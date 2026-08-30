package com.smart_logistics.backend.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiWarehouseMigrationTest {

    private static final Pattern TASK_CARGO_TYPE_COLUMN = Pattern.compile(
            "alter\\s+table\\s+transport_task\\s+add\\s+column\\s+cargo_type_id",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Test
    void migrationDefinesMultiWarehouseTablesColumnsIndexesAndForeignKeys()
            throws Exception {
        String migration = Files.readString(Path.of("..", "docs", "sql",
                "014_multi_warehouse_inventory.sql"));
        String normalized = migration.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        assertTrue(normalized.contains("create table cargo_type"));
        assertTrue(normalized.contains("create table warehouse"));

        assertTrue(normalized.contains("add column cargo_type_id bigint null"));
        assertTrue(normalized.contains("add column warehouse_id bigint null"));
        assertTrue(normalized.contains("add column origin_warehouse_id bigint null"));

        assertTrue(normalized.contains("unique key uk_cargo_type_name (name)"));
        assertTrue(normalized.contains("unique key uk_warehouse_no (warehouse_no)"));
        assertTrue(normalized.contains("index idx_cargo_type_warehouse_status"
                + " (cargo_type_id, warehouse_id, status)"));
        assertTrue(normalized.contains("index idx_cargo_warehouse_id (warehouse_id)"));
        assertTrue(normalized.contains("index idx_vehicle_warehouse_status"
                + " (warehouse_id, status)"));
        assertTrue(normalized.contains("index idx_task_origin_warehouse"
                + " (origin_warehouse_id)"));

        assertTrue(normalized.contains("constraint fk_cargo_cargo_type"
                + " foreign key (cargo_type_id) references cargo_type(id)"));
        assertTrue(normalized.contains("constraint fk_cargo_warehouse"
                + " foreign key (warehouse_id) references warehouse(id)"));
        assertTrue(normalized.contains("constraint fk_vehicle_warehouse"
                + " foreign key (warehouse_id) references warehouse(id)"));
        assertTrue(normalized.contains("constraint fk_task_origin_warehouse"
                + " foreign key (origin_warehouse_id) references warehouse(id)"));

        assertFalse(TASK_CARGO_TYPE_COLUMN.matcher(migration).find());
    }

    @Test
    void migrationKeepsTheRequiredOperationOrder() throws Exception {
        String migration = Files.readString(Path.of("..", "docs", "sql",
                "014_multi_warehouse_inventory.sql"));
        String normalized = migration.toLowerCase(Locale.ROOT);

        int cargoTypeTable = normalized.indexOf("create table cargo_type");
        int warehouseTable = normalized.indexOf("create table warehouse");
        int alterCargoColumns = normalized.indexOf("alter table cargo");
        int alterVehicleColumns = normalized.indexOf("alter table vehicle");
        int alterTaskColumns = normalized.indexOf("alter table transport_task");
        int cargoForeignKeys = normalized.indexOf("constraint fk_cargo_cargo_type");

        assertTrue(cargoTypeTable >= 0);
        assertTrue(cargoTypeTable < warehouseTable);
        assertTrue(warehouseTable < alterCargoColumns);
        assertTrue(alterCargoColumns < alterVehicleColumns);
        assertTrue(alterVehicleColumns < alterTaskColumns);
        assertTrue(alterTaskColumns < cargoForeignKeys);
    }
}
