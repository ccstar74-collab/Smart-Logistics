# Frontend contract freeze — Phase 0

This document is an incremental contract freeze for the Phase 1 frontend P0 API work. It does not replace earlier API specifications.

## Frozen values and names

- `UserStatus`: `ACTIVE`, `DISABLED`. `ENABLED` is not part of the contract.
- `UserRole`: `OWNER`, `DRIVER`, `WAREHOUSE_MANAGER`, `DISPATCHER`, `ADMIN`.
- The Alarm list filter parameter is `alarmType`; no `type` alias is provided.
- `DispatchCommandStatus` remains `PENDING`, `EXECUTED`, `CANCELLED`, `FAILED`.
- Dispatch status semantics must be frozen again with the realtime backend in Phase 4. This phase does not add `READ`, `PROCESSING`, or similar states.

## Ownership of state and relationships

- Cargo status cannot be changed through an independent Cargo status endpoint.
- `PUT /api/v1/transport-tasks/{id}/status` remains the only business write entry point for coordinated TransportTask, Cargo, and Vehicle transport status changes.
- Cargo-to-Vehicle assignment and its history are represented by `TransportTask.cargoId` and `TransportTask.vehicleId`; the current driver relationship is represented by `Vehicle.driverId`.
- No `bindings` API, module, entity, mapper, service, table, or controller is introduced.

## Realtime boundary

This phase does not implement MQTT endpoints or listeners, location/status/event ingestion, command acknowledgement, WebSocket, tracking, ETA calculation, or any other realtime backend API.
