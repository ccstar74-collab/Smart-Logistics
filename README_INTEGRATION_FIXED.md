# Smart Logistics Frontend — Backend Integration Fixed

This package is aligned to the backend integration contract dated 2026-08-31.

Key fixes:

- API base remains `/api/v1`, development proxy target remains `http://111.170.148.177:58080`.
- Multi-warehouse flow uses `origin-recommendation`, `cargos/available`, `vehicles/available`, `initial-route-decisions`, and `transport-tasks/from-warehouse`.
- Owner dropdown now prioritizes backend `ownerId` rather than a generic/user `id`.
- HTTP 404 preserves backend `message` / business code instead of always displaying “endpoint not found”.
- Initial route confirmation sends `routeDecisionId` + `selectedRouteId` and stable `Idempotency-Key` values.
- Cargo/vehicle IDs are reloaded after business-resource conflicts; 404/40401 no longer sends users to debug the API port blindly.
- Unknown traffic/weather values are shown as unavailable rather than inferred as normal.
- No `driverId` or `start*` fields are submitted by the multi-warehouse task-creation page.

Verification performed in the working environment:

- `npm test`: 11/11 passed.
- `src/api/http.js`: JavaScript syntax check passed.
- `src/views/CargoOutbound.vue`: Vue SFC parse/script/template compile check passed.
- Full Vite build was not run successfully in the Linux container because the uploaded Windows `node_modules` lacks Rollup's Linux optional native package. Reinstall dependencies locally with `npm install` before building.
