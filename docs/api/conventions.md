# API Conventions

- Base URL: `/api/v1/{domain}/{resource}`
- Authentication: `Authorization: Bearer <JWT>`
- All requests require `X-Correlation-ID` (auto-generated if absent)
- Multi-tenancy: `X-Tenant-Id` header
- Pagination: `?page=0&size=20&sort=createdAt,desc`
- Response envelope: `{ data: ..., meta: {...} }` for success,
  `{ error: { code, message, details } }` for errors
- Idempotency: `Idempotency-Key` header on POST
- Versioning: `/api/v1/...`, `/api/v2/...`

## REST conventions

- Nouns for resources: `/patients`, `/appointments`
- Actions as sub-resources: `POST /patients/{id}/cancel`, `POST /orders/{id}/submit`
- Status codes: 200 OK, 201 Created, 204 No Content, 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 409 Conflict, 422 Unprocessable Entity, 429 Too Many Requests, 500 Internal Server Error
