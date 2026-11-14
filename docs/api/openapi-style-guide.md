# SQUAMED OpenAPI style guide

This describes the contract conventions the backend already ships and the PIT suite
(`errorResponsesFollowContract`, `paginationDelivered`, `openApiSnapshotMatchesLiveContract`)
enforces. New endpoints follow these rules or contract tests fail.

## Base URL and versioning

- All HTTP APIs are versioned in the path: `/api/v1/…`. Breaking changes require a new version
  (`/api/v2/…`); additive changes (new optional fields, new endpoints) stay in `/api/v1`.
- The live machine-readable contract is served at `/api-docs` (springdoc OpenAPI 3.1). The
  committed snapshot lives at `docs/contracts/openapi-v1.json` and must match it byte-for-byte
  after removing the environment-dependent `servers` block (see contract-codegen.md).

## Response envelope

Every success response is wrapped in the common envelope (`com.squamed.core.common.api.ApiResponse`):

```json
{
  "data": { "...resource or array..." },
  "success": true,
  "meta": {}
}
```

- `data` is the resource (or resource array). Never put transport metadata inside `data`.
- `success` is always `true` on 2xx; errors use the error envelope below.
- `meta` carries paging and other cross-cutting metadata; empty object when not paged.

## Error envelope

All 4xx/5xx responses use the error envelope (`GlobalExceptionHandler.ErrorBody`):

```json
{
  "error": {
    "code": "MACHINE_READABLE_CODE",
    "message": "Human-readable summary",
    "details": [{ "field": "dateOfBirth", "message": "must not be null" }]
  },
  "timestamp": "2026-11-14T10:15:30Z"
}
```

- `code` is a stable machine-readable identifier (`UPPER_SNAKE_CASE`), e.g. `VALIDATION_FAILED`,
  `IDEMPOTENCY_CONFLICT`. Clients branch on `code`, never on `message` text.
- `message` is safe to display; never include stack traces or internal paths.
- `details` is used by validation errors to list per-field failures.

## Pagination

List endpoints accept `?page=0&size=25` (Spring Pageable semantics, page is 0-based) and return:

```json
{
  "data": [ "..." ],
  "meta": { "page": 0, "size": 25, "total": 132, "pages": 6 }
}
```

Paging lives in `meta`, never in `data`. Unpaged single-resource responses keep `meta: {}`.

## Idempotency-Key

All unsafe methods (POST/PUT/PATCH/DELETE) accept an optional `Idempotency-Key` header
(≤ 255 chars). With a JSON body:

- First execution: processed normally, the response is stored under
  `(Idempotency-Key, method, endpoint)`.
- Retry with the same key + same payload: the stored response is replayed byte-for-byte and the
  request never reaches the controller (no duplicate side effects, no duplicate audit rows — the
  audit trail records the first execution only, by design).
- Retry with the same key + different payload: `409` with code `IDEMPOTENCY_CONFLICT`.
- 5xx responses are never stored; the retry re-executes.

Generate keys client-side as UUIDs per logical operation. Reuse the same key across retries of
that operation, including across process restarts.

## Naming

- Paths: plural kebab-case resource collections (`/api/v1/patients`, `/api/v1/hospital-config`).
- JSON fields: camelCase. Enums: lowercase snake_case values (`blood_group: "o_positive"`).
- Event names (in event payloads, not HTTP): lowercase dotted domain names, `patient.created`.
- Timestamps: ISO-8601 UTC instants.
