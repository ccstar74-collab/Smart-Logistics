-- Add mapping between business vehicles and realtime GPS simulator vehicle IDs.
-- Applied to the cloud database on 2026-08-25.
--
-- sim_code stores the vehicleID used by GPS data in InfluxDB,
-- for example: sim_000.
--
-- This migration only adds the nullable mapping column.
-- It does not assign sim_code values to existing vehicles.

ALTER TABLE vehicle
ADD COLUMN sim_code VARCHAR(64) NULL
COMMENT 'InfluxDB中gps的vehicleID，例如sim_000';
