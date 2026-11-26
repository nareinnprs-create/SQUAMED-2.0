## 07.10 — config-only specialty pack installer

Turns 07.10's "installer not built" into a working capability. The 22 pack **contents** still need human clinical review; the mechanism that applies them no longer does.

### Schema (`V108`, additive)
- `pack.specialty_pack`, `pack.pack_component`, `pack.pack_install` (unique per hospital + pack + version), permissions `pack:read|manage|install:pack` for SYSTEM_ADMIN and HOSPITAL_ADMIN.

### Backend (`/api/v1/packs`)
- **POST /** — register a versioned pack: `CONFIG` components (key must be an existing config type, payload validated by that type's JSON schema at install) and `ROLE` components (permission codes resolved against `identity.permission` at registration, so an unknown grant fails before anything is written).
- **POST /{code}/install** — publishes each CONFIG document through the normal validated path, ensures the role and its grants, records the install. Idempotent: a second install of the same version returns `ALREADY_INSTALLED` without republishing.
- **GET /**, **GET /installs** — pack catalogue and hospital-scoped install history.

### Evidence
- New PIT `specialtyPackInstallCoverage`: a reference cardiology pack (task-sla config + CARDIOLOGY role) installs and the published config is readable with `defaultSlaHours=48` and the pack's role on the escalation rule; re-install is a no-op with one recorded install; an unknown config type and an unknown permission are both rejected 400; a second hospital sees an empty install history.

### What is still owed
- The 22 clinically reviewed pack contents. The installer is generic; the contents are the reviewed artefacts.
- The exit demo's "cardiology pack installs via configuration alone in < 1 day" step can now run against a real installer with the reference pack; the full 22-pack library remains gated on review.
