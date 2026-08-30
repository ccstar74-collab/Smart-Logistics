# Multi-objective route test guide

## Prerequisites

- Start from `integration/warehouse-route-ws` or a feature branch based on it.
- Apply route migrations `009`, `013`, and `015` in the target MySQL schema.
- Configure `AMAP_WEB_SERVICE_KEY` as an environment variable.
- Use a task in `WAITING` or `TRANSPORTING` with complete start/end coordinates,
  one current `ACTIVE` route, and an assigned driver.
- Prepare one `DISPATCHER` account and the assigned `DRIVER` account.

## Automated regression

Run from `backend`:

```powershell
.\mvnw.cmd '-Dtest=AmapEtaRouteProviderTest,MultiObjectiveRoutePlanningServiceTest,TransportTaskRouteServiceTest,DispatchCommandServiceTest,TransportTaskControllerTest' test
.\mvnw.cmd test
```

The full suite must finish with zero failures and zero errors.

## REST state-machine test

Set the backend address and replace the account placeholders locally. Do not
commit real passwords or API keys.

```powershell
$baseUrl = 'http://127.0.0.1:18080'
$taskId = 1

$dispatcherLogin = Invoke-RestMethod -Method Post `
  -Uri "$baseUrl/api/v1/auth/login" `
  -ContentType 'application/json' `
  -Body '{"username":"<dispatcher>","password":"<password>"}'
$dispatcherHeaders = @{
  Authorization = "Bearer $($dispatcherLogin.data.accessToken)"
}

$driverLogin = Invoke-RestMethod -Method Post `
  -Uri "$baseUrl/api/v1/auth/login" `
  -ContentType 'application/json' `
  -Body '{"username":"<assigned-driver>","password":"<password>"}'
$driverHeaders = @{
  Authorization = "Bearer $($driverLogin.data.accessToken)"
}
```

Generate candidates and capture one target route:

```powershell
$candidates = Invoke-RestMethod -Method Post `
  -Uri "$baseUrl/api/v1/transport-tasks/$taskId/routes/candidates" `
  -Headers $dispatcherHeaders

$routesBefore = Invoke-RestMethod -Method Get `
  -Uri "$baseUrl/api/v1/transport-tasks/$taskId/routes" `
  -Headers $dispatcherHeaders

$targetRouteId = $candidates.data[0].routeId
$routesBefore.data | Select-Object routeId, routeVersion, routeStatus,
  distanceMeters, referenceDurationSeconds
```

Expected: exactly one existing `ACTIVE` route and at least two new `READY`
routes with consecutive versions.

Create a normal route-change command:

```powershell
$commandBody = @{
  taskId = $taskId
  commandType = 'ROUTE_CHANGE'
  content = 'Switch to selected multi-objective route'
  routeId = $targetRouteId
} | ConvertTo-Json

$command = Invoke-RestMethod -Method Post `
  -Uri "$baseUrl/api/v1/dispatch-commands" `
  -Headers $dispatcherHeaders `
  -ContentType 'application/json' `
  -Body $commandBody
$commandId = $command.data.id
```

ACK must not switch the route:

```powershell
Invoke-RestMethod -Method Patch `
  -Uri "$baseUrl/api/v1/dispatch-commands/$commandId/status" `
  -Headers $driverHeaders `
  -ContentType 'application/json' `
  -Body '{"status":"ACKNOWLEDGED"}'

$routesAfterAck = Invoke-RestMethod -Method Get `
  -Uri "$baseUrl/api/v1/transport-tasks/$taskId/routes" `
  -Headers $driverHeaders
$routesAfterAck.data | Select-Object routeId, routeVersion, routeStatus
```

Expected: the old route is still `ACTIVE`; the target is still `READY`.

EXECUTING performs the transactional switch:

```powershell
Invoke-RestMethod -Method Patch `
  -Uri "$baseUrl/api/v1/dispatch-commands/$commandId/status" `
  -Headers $driverHeaders `
  -ContentType 'application/json' `
  -Body '{"status":"EXECUTING"}'

$routesAfterExecuting = Invoke-RestMethod -Method Get `
  -Uri "$baseUrl/api/v1/transport-tasks/$taskId/routes" `
  -Headers $driverHeaders
$plannedRoute = Invoke-RestMethod -Method Get `
  -Uri "$baseUrl/api/v1/transport-tasks/$taskId/planned-route" `
  -Headers $driverHeaders

$routesAfterExecuting.data | Select-Object routeId, routeVersion, routeStatus
$plannedRoute.data | Select-Object routeId, routeVersion, routeStatus
```

Expected:

- old `ACTIVE` becomes `INACTIVE`;
- selected `READY` becomes the only `ACTIVE` route;
- `planned-route` returns the selected route immediately;
- `activatedAt` and `deactivatedAt` are populated at the switch;
- other unselected candidates remain `READY`.

## Negative checks

- Calling the removed public
  `PUT /api/v1/transport-tasks/{taskId}/routes/{routeId}/activate` must return
  resource-not-found behavior.
- A non-dispatcher cannot generate candidates.
- A route outside the task or a route not in `READY` cannot be used for
  `ROUTE_CHANGE`.
- If test data is deliberately corrupted to contain two `ACTIVE` routes, route
  lookup must fail with `DATA_CONFLICT` instead of choosing the latest version.
- FAST_RECOVERY replan must still create and activate its route directly without
  waiting for Agent scoring or a normal route-change command.
