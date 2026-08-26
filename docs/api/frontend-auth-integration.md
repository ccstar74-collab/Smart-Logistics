# Frontend authentication integration

1. Register with `POST /api/v1/auth/register`. Allowed roles are `OWNER`, `DRIVER`, and
   `WAREHOUSE_MANAGER`. Registration does not return a token.
2. Log in with `POST /api/v1/auth/login`. The login request contains only `username` and
   `password`; never send a role because the server loads it from the database.
3. Store the returned `accessToken` using the frontend's approved token-storage policy.
4. Send `Authorization: Bearer <token>` on every other `/api/v1/**` request.
5. On application startup call `GET /api/v1/users/me` to reload the current identity and
   role.
6. On HTTP 401, remove the invalid token and return to the login page.
7. On HTTP 403, show a no-permission state. Do not treat 403 as an expired login.

Formal role names are `OWNER`, `DRIVER`, `WAREHOUSE_MANAGER`, `DISPATCHER`, and `ADMIN`.
The old `WAREHOUSE` value is invalid.
