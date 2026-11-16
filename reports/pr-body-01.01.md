# feat(01.01): scheduling engine — templates, idempotent slot generation, booking, waitlist, teleconsult

Chunk-01 §01.01. Branch `feat/01.01-scheduling-engine` off main `1ae6379`.

## Backend (`backend/squamed-core`)
- `V54__scheduling_engine.sql`: `schedule_template`, `session_slot`, `waitlist_entry` (all `hospital_id NOT NULL`), scheduling policy config schema, grants (RECEPTIONIST full scheduling writes, DOCTOR scheduling read-only, SYSTEM_ADMIN explicit), 4 new `appointment.*` event types. Weekday column `INTEGER` (V54 in-place corrected before apply; rollback note in-file).
- `SchedulingService`: template CRUD with guards (`SCHEDULE_WEEKDAY_INVALID`, `SCHEDULE_END_BEFORE_START`, …), **idempotent slot generation** on read upserting on the unique `(hospital, doctor, date, start)` key (window clamped to 14 days), slot booking (capacity/overbook ceiling from published policy, no-show window+limit with `scheduling:override:booking` override, teleconsult slots set `telemedicine=true`), cancel releases the slot, reschedule moves it, waitlist with urgent> vip> routine fairness and promote-to-slot.
- `SchedulingController` (`/api/v1/schedules/**`), `AppointmentController` lifecycle PATCH, audit coverage on writes.
- `EventCatalog`: +`AppointmentCreated/Cancelled/Rescheduled/StatusChanged` (17 types).
- `ScheduleTemplate.equipmentIds` jsonb via `@JdbcTypeCode(SqlTypes.JSON)`.

## Frontend (`apps/web`, `packages/ui`)
- `/appointments` slot picker: labelled radio-group over house primitives, full slots disabled with text; `appointments.slot.*` i18n keys in en + hi.

## Contract
- `docs/contracts/openapi-v1.json` regenerated (snapshot sha `aff1a71e…`) + `packages/api-client/src/generated/schema.d.ts` — same-PR rule.

## Verification (all local, 2026-11-17)
- PIT **60/60** (external mode, compose PG) incl. 3 `SchedulingEngineTests`.
- Turbo **28/28** tasks: typecheck+lint clean, tests **146/146** (web 88, ui 48, api-client 5, schemas 5), `next build` green.
- VRT **6/6** baselines match (`/appointments` unchanged — picker renders on selection).
- Exit demo `scripts/demo-01.01-scheduling-engine.ps1` **21/21**.

## Notes
- Deviations (guard 400-vs-409 mapping, RECEPTIONIST write grant, TIME-column JVM-offset wire shift, LocalTime seconds serialization, RLS deferred to 00.04) recorded in `reports/chunk-01/section-01.01.md` §9.
- One real flakiness fix found en route: the lifecycle PIT test now creates a fresh hospital per run (slot upsert keys in the shared seed hospital shadowed a new template's slots across runs).
