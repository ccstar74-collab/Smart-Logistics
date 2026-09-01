# Backend Integration Fix — 2026-08-31

This frontend is aligned to the deployed backend contract at:

- API Base URL: `http://111.170.148.177:58080/api/v1`
- Initial route decision: `POST /initial-route-decisions`
- Restore decision: `GET /initial-route-decisions/{decisionId}`
- Warehouse recommendation: `POST /transport-tasks/origin-recommendation`
- Available cargo: `GET /cargos/available?ownerId=...&cargoTypeId=...&warehouseId=...`
- Available vehicle: `GET /vehicles/available?warehouseId=...`
- Create task: `POST /transport-tasks/from-warehouse`

Important frontend fixes:

1. Owner options now prefer `ownerId` over generic `id` to avoid sending a User ID where the backend requires the Owner business ID.
2. HTTP 404 keeps the backend business message/code instead of always reporting “endpoint not found”.
3. Final task confirmation keeps `routeDecisionId` and `selectedRouteId`, uses a stable `Idempotency-Key`, and never submits route geometry/scores.
4. The multi-warehouse task DTO uses `planStartTime` / `planEndTime`. The separate initial-route document contains `plannedStartTime`; this package preserves `planStartTime`, consistent with the main multi-warehouse contract and existing task model.
5. Cargo/vehicle availability queries use numeric IDs and preserve the required `ownerId + cargoTypeId + warehouseId` combination.
6. `AGENT_EXPLANATION`, `RULE_FALLBACK`, and UNKNOWN traffic/weather states are displayed without overriding backend scoring/ranking.
