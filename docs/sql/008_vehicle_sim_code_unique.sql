-- Enforce the one-to-one mapping between a GPS device code and a vehicle.
-- MySQL UNIQUE indexes allow multiple NULL values, preserving historical vehicles.
-- Before deployment, verify that no duplicate non-null values exist:
-- SELECT sim_code, COUNT(*) FROM vehicle
-- WHERE sim_code IS NOT NULL GROUP BY sim_code HAVING COUNT(*) > 1;

ALTER TABLE vehicle
ADD UNIQUE INDEX uk_vehicle_sim_code (sim_code);
