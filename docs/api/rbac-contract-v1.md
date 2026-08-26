# Smart-Logistics RBAC Contract V1

## Formal roles

The only business roles are `OWNER`, `DRIVER`, `WAREHOUSE_MANAGER`, `DISPATCHER`, and
`ADMIN`. The legacy `WAREHOUSE` value is not accepted. `WAREHOUSE_MANAGER` has no
`ownerId` or `driverId` identity.

## Self-registration

| Role | Public registration |
|---|---|
| OWNER | allowed; creates User and Owner atomically |
| DRIVER | allowed; creates User and Driver atomically |
| WAREHOUSE_MANAGER | allowed; creates User only |
| DISPATCHER | denied |
| ADMIN | denied |

Registration never issues a JWT. The client logs in after registration.

## Authorization and data scope

| Role | Scope and responsibility |
|---|---|
| OWNER | Exact owner relationship. Read only own Cargo, Cargo-related Tasks, their Vehicles, and Alarms. |
| DRIVER | Exact driver relationship. Read own Vehicle and assigned Task/Cargo/Alarm; only allowed external Task status transitions. |
| WAREHOUSE_MANAGER | V1 simplified warehouse scope: all warehouse business Cargo, Vehicle, and Task allocation data because no warehouse relation exists. |
| DISPATCHER | V1 simplified dispatcher scope: read all dispatch business Cargo, Vehicle, Task, and Alarm data because no dispatcher assignment relation exists. |
| ADMIN | Read-all business data and maintain Cargo/Vehicle base data; not a super-role and not allowed to mutate Tasks or Alarms. |

Security scope is always combined with user filters using logical `AND`. An explicit
conflicting `ownerId` or `driverId` is rejected with HTTP 403.

## Business permission decisions

- Task creation: `WAREHOUSE_MANAGER` only.
- Task base update: `WAREHOUSE_MANAGER` only and only while `WAITING`.
- External Task status update: `DRIVER` only, for the current driver's Task, limited to
  `WAITING -> TRANSPORTING` and `TRANSPORTING -> COMPLETED`.
- Future dispatch commands: `DISPATCHER`; not implemented in this V1 increment.
- Alarm status: no frontend role currently has permission.
- Cargo direct status endpoint does not exist; status remains driven by TransportTask.
- A separate `/bindings` API or binding model does not exist.
- Cargo deletion is allowed for `WAREHOUSE_MANAGER` and `ADMIN` only. The Cargo must
  be `WAITING` and must not have an active `WAITING` or `TRANSPORTING` Task.
- CargoItem writes are a derived permission of Cargo base-information maintenance for
  `WAREHOUSE_MANAGER` and `ADMIN`.

## Phase 5 query permissions

- Vehicle latest/history locations and Task track points reuse the existing Vehicle/Task
  DataScope for all five formal roles.
- Cargo status records reuse Cargo DataScope and are read-only.
- Alarm status mutation remains denied to every frontend role in V1. `ADMIN` is not a
  super-role and `DISPATCHER` remains read-only until a later formal contract revision.
