`.gate/` is **read-only to build agents** (protocol §7.1).
Changes require a protocol §7 amendment plus orchestrator and product-owner approval.
`scripts/gate-parity.mjs` (CI job `gate-parity`) enforces sync between `GATES.md` and `.github/workflows/ci.yml`.
Gate registry lives in `GATES.md`.
