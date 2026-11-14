# JWT signing-key rotation (00.03)

JWTs are signed with HMAC-SHA256 keys selected by `kid` (key ID) in the token header.

## Modes

- **Legacy (default):** `squamed.security.jwt.keys` is empty → single secret from
  `squamed.security.jwt.secret`, header `kid: default`. All existing tokens verify.
- **Rotation:** `keys` maps kid → secret. Tokens are signed with `keys[active-kid]`;
  verification pins the header kid to its configured key. Tokens minted before a
  rotation carry no kid and are tried against every configured key.

## Rotate

1. Deploy with `SQUAMED_SECURITY_JWT_KEYS={default:<current-secret>,new:<new-secret>}`
   and `SQUAMED_SECURITY_JWT_ACTIVE_KID=new`.
2. Old tokens keep verifying (pinned `default` kid, or kid-less try-all) until they
   expire: access 15 min, refresh 7 days, MFA 10 min.
3. After the old-token TTLs lapse, drop `default` from the map (or flip back to
   single-secret mode with the new secret once no old tokens remain).

## Rules

- Every key must be ≥ 32 bytes (HMAC-SHA256 requirement).
- Startup fails fast if `active-kid` is missing from `keys`.
- An unknown `kid` in a token header always fails verification.
