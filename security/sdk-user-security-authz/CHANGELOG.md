# Changelog — sdk-user-security-authz

## 3.0.0

First release as its own artifact; the policy enforcer previously shipped inside
`sdk-user-security` 2.x.

### Breaking
- Package `io.codehunters.sdk.security.authz` → `io.codehunters.security.authz`.
- Properties `keycloak.policy-enforcer.*` → `codehunters.security.authz.*`; Keycloak URL and realm
  come from `codehunters.security`. Old keys are honoured until 4.0.
- Old keys are read the way Boot binds them, so an environment variable may spell a dashed name
  with or without underscores (`KEYCLOAK_POLICY_ENFORCER_ENFORCEMENT_MODE` or `KEYCLOAK_POLICYENFORCER_ENFORCEMENTMODE`). Only the documented old keys are aliased, and
  startup lists old keys ignored because the new key is set.
- `KeycloakAuthzFilter` takes a `Supplier<AuthzClient>`; no `AuthzClient` bean is created.
- A method the catalog does not list for a matched path is denied.

### Fixed
- Startup failed when Keycloak was unreachable, because the client fetched discovery eagerly.
- Unlisted methods on catalogued paths passed unenforced; the first declared pattern, not the most
  specific, decided.
- `enforcement-mode` was ignored. Keycloak outages answered 403 instead of 503.
- Calls to Keycloak had no timeouts; they now have connect/read timeouts and a retry back-off.

### Changed
- `keycloak-authz-client` 25.0.6 → 26.0.12.
