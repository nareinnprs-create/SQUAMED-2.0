# Contract codegen workflow

The API contract is committed as a snapshot and code-generated into typed clients. Two tests
keep everything honest:

- Backend PIT `openApiSnapshotMatchesLiveContract` — the committed snapshot must equal the live
  `/api-docs` document (the environment-dependent `servers` block is removed before comparison).
  Any contract drift fails the integration gates.
- Frontend `packages/api-client/src/generated/schema.test.ts` — the generated types carry a
  sha256 stamp of the snapshot, so generated code can never be older than the committed contract.

## When the contract changes

1. Make the backend change (new endpoint, new field, new error code).
2. Regenerate the snapshot **and** the client types in the same PR:

   ```bash
   # from the repo root, with the dev stack running (scripts/dev-up.sh) and the API on :8080
   curl -s http://localhost:8080/api-docs -o docs/contracts/openapi-v1.json

   # regenerate the TS types + sha256 stamp
   npm run codegen -w @squamed/api-client
   ```

3. Commit both files together:
   - `docs/contracts/openapi-v1.json` — the contract snapshot
   - `packages/api-client/src/generated/schema.d.ts` — the generated types (stamped)
4. If CI fails with `OpenAPI contract drifted from the committed snapshot`, you forgot step 2 on
   the backend side. If `schema.test.ts` fails, you forgot the codegen on the client side.

## Notes

- The generated file is committed (not gitignored) so frontend builds and CI typecheck never
  depend on a running backend.
- The snapshot comparison normalizes only the `servers` block; every other diff (path, schema,
  response code) is a contract change and must be reviewed.
- Breaking changes to `/api/v1` need a version bump — see `docs/api/openapi-style-guide.md`.

