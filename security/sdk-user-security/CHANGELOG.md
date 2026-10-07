# Changelog — sdk-user-security

## 3.0.0

### Breaking
- Identity and authorities come from the bearer token, verified locally against the realm's JWK set
  (signature, issuer, expiry, subject, optional audience). Gateway `x-user-id` / `x-user-roles`
  headers must agree with the token or the request is rejected with 401.
- Package `io.codehunters.sdk.security` → `io.codehunters.security`.
- Properties `keycloak.security.*` → `codehunters.security.*`; introspection settings move under
  `codehunters.security.introspection`. Old keys are honoured until 4.0 with a warning.
- Old keys are read the way Boot binds them, so an environment variable may spell a dashed name
  with or without underscores (`KEYCLOAK_SECURITY_CLIENT_SECRET` or `KEYCLOAK_SECURITY_CLIENTSECRET`). Only the documented old keys are aliased, and
  startup lists old keys ignored because the new key is set.
- `fail-open` defaults to `false`.
- No longer gated by the `develop`/`cert`/`prod` profiles; use `codehunters.security.enabled`.
- `codehunters.security.audiences` is required (or `allow-any-audience=true`).
- `public-paths` / `excluded-paths` use `PathPattern` syntax; `**` in the middle of a pattern fails
  startup.
- The Keycloak policy enforcer moved to `sdk-user-security-authz`.

### Fixed
- Forged `x-user-roles` could grant any role when the caller bypassed the gateway.
- Missing `url`/`realm` made every introspection fail and, with fail-open, allowed every request;
  required settings are now validated at startup.
- The introspection client had no timeouts and its cache could grow without bound; raw tokens were
  cache keys. Calls now have connect/read timeouts, the cache is a size-capped LRU keyed by SHA-256,
  and failures are never cached.
- Role names were upper-cased with the default locale (`admin` → `ROLE_ADMİN` under Turkish).
- Malformed `x-user-roles` JSON fell back to CSV parsing and produced junk roles; it is rejected.

### Added
- `issuer-uri` and `jwk-set-uri` overrides; `method-security.enabled`.
- The key set is cached and rate-limited; unknown key ids answer 401 without refetching it.

### Removed
- Dependencies on AspectJ, Micrometer, `spring-aop` and `spring-expression`, which were unused.
