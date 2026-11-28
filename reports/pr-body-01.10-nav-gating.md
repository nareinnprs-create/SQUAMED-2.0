01.10's contract says *"reseller plans hide modules"*. The backend refused a gated module, but only AI and BI were actually gated, and the shell still offered links to modules the plan did not include. This closes that gap end to end.

## What changed

**Backend — the plans now gate what they advertise.** `CORE` lists `ai, bi, scribe, portal, kiosk, fhir, twin, packs, telemedicine` as excluded, but only `ai` and `bi` had a gate. Added `@RequiresFeature("scribe")` to `ScribeController` and `@RequiresFeature("telemedicine")` to `TelemedicineController`, so a CORE hospital gets `403 ENTITLEMENT_REQUIRED` on those too.

**Frontend — the shell stops offering what the API refuses.** `apps/web/src/lib/nav-entitlements.ts` maps nav ids to features (`ai`, `scribe`) and filters them; the layout fetches `/api/v1/entitlements` once. Three behaviours, deliberately:

- `managed=false` (UNMANAGED) → show everything, matching the API.
- the entitlement call **fails** → show everything. An entitlement outage must not empty the menu.
- a nav item with no feature mapping → never hidden.

## Tests

- `entitlementGatingCoverage` now asserts CORE refuses `/api/v1/scribe/sessions` and `/api/v1/telemedicine/consults`, and that FULL restores both.
- `nav-entitlements.test.ts` (4 cases): unmanaged/failed → all items; CORE → AI and scribe hidden; FULL → all; unmapped item → never hidden.
- `npm run typecheck -w @squamed/web` clean; `npm test -w @squamed/web` **126 passed**; `npm run lint` 0 errors (3 pre-existing warnings elsewhere).

## Why this is not scope creep

The feature names `scribe` and `telemedicine` were already in the seeded plans. A plan that lists a feature it does not gate is worse than no plan: it tells a reseller they can sell a tier that the platform does not actually enforce.

Backend full chain: see CI on this PR.
