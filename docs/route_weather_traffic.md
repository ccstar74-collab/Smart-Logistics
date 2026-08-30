# Route weather and traffic data

## Weather API

`GET /api/v1/transport-tasks/{taskId}/route-data/weather`

The backend uses the task destination coordinate as the weather sample point,
resolves it to an Amap `adcode`, and then requests live weather. The response
contains:

- `source`
- `adcode`, `province`, `city`
- `weather`, `temperature`, `humidity`
- `windDirection`, `windPower`
- `reportTime`

The endpoint is read-only. Provider failures return service-unavailable instead
of a successful response with invented or stale weather.

## Route traffic snapshot

Amap route requests use `extensions=all`. Each new immutable route snapshot
includes a nullable `traffic` object with:

- provider source and returned planning strategy;
- restriction flag and traffic-light count;
- observed distance grouped as unknown, smooth, slow, congested, and severely
  congested.

Apply `docs/sql/015_route_traffic_snapshot.sql` before deploying this version.
Historical route rows remain `NULL`; new route planning persists traffic facts
together with distance, duration, and geometry.

Required environment variables:

```text
AMAP_WEB_SERVICE_KEY
```

Optional endpoint overrides:

```text
AMAP_ROUTE_ENDPOINT
AMAP_REGEO_ENDPOINT
AMAP_WEATHER_ENDPOINT
AMAP_REQUEST_TIMEOUT
```
