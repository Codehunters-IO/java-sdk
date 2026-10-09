# Changelog — sdk-user-security-authz

## 5.0.0

### Breaking
- Requires `sdk-user-security` 5.0.0. See [MIGRATION-5.0.md](MIGRATION-5.0.md).
- The in-house enforcement point is replaced by Keycloak's official policy enforcer, running inside
  the Spring Security filter chain. Its semantics apply; see Behaviour changes.

### Removed
- The `keycloak.policy-enforcer.*`, `keycloak.host` and `keycloak.realm` aliases. Only
  `codehunters.security.authz.*` and `codehunters.security.url` / `realm` are read; an old key is
  ignored.
- `KeycloakAuthzLegacyProperties`, its `spring.factories` entry and the deprecated `keycloak.*`
  configuration metadata.
- The dependency on `sdk-support`.
- `KeycloakAuthzFilter`, its servlet `FilterRegistrationBean` and the use of an application
  `AuthzClient` bean.
- The dependency on `keycloak-authz-client` (now brought by `keycloak-policy-enforcer`).

### Added
- `KeycloakPolicyEnforcerFilter`, running Keycloak's `PolicyEnforcer` the way its
  `ServletPolicyEnforcerFilter` does, with `KeycloakPolicyEnforcerFilter.Options` (token resolver,
  retry backoff, clock, failure listener), and the `keycloakPolicyEnforcerCustomizer`
  (`Customizer<HttpSecurity>`) that places it after `AuthorizationFilter` in every
  `SecurityFilterChain`.
- The `keycloakPolicyEnforcerHttpClient` bean (Apache `CloseableHttpClient`, not a default
  candidate), closed with the context.
- The `codehunters.authz.enforcer.failures` counter, tagged `stage` (`enforcer`, `call`), when
  Micrometer and a `MeterRegistry` are present. Failed calls to Keycloak are logged at `WARN`.
- An application `BearerTokenResolver` bean is used to read tokens the authentication no longer
  holds.
- Dependency `org.keycloak:keycloak-policy-enforcer` 26.0.12.
- Properties mapped to Keycloak's `PolicyEnforcerConfig`: `lazy-load-paths`,
  `http-method-as-scope`, `path-cache.max-entries`, `path-cache.lifespan`,
  `claim-information-point.*`, `max-connections`, `paths[].enforcement-mode`, `paths[].scopes`,
  `paths[].claim-information-point.*` and `paths[].methods[].scopes-enforcement-mode`.
- Startup fails when a listed method has no scopes, a path uses `**` or does not start with `/`,
  or an enforcement mode is unknown.

### Changed
- `codehunters.security.authz.retry-backoff` now defaults to `10s` (was `30s`) and applies to
  building Keycloak's enforcer: after a failed build, enforced requests answer 503 for that long
  without calling Keycloak, then a single request rebuilds.

### Behaviour changes
- Requests a bearer token authenticated are enforced, decided from the authentication rather than
  the `Authorization` header: tokens an application `BearerTokenResolver` reads from a query
  parameter or another header are enforced too. A request another chain authenticated otherwise
  (basic auth on actuator, for example) passes through. A token authentication whose token cannot
  be found is denied (403).
- Paths are matched decoded and without path parameters, as Spring MVC routes them.
- Unauthenticated requests are answered by Spring Security (401 on protected paths) before
  Keycloak is asked; anonymous requests on public and excluded paths are not enforced.
- Authenticated requests to paths not in the catalog are denied (403) under `ENFORCING`;
  `PERMISSIVE` lets them pass and no longer means "never block".
- Patterns are Keycloak's (`/*`, `{id}`), not Ant's.
- A method a path does not list falls back to the path's `scopes` (or the HTTP method with
  `http-method-as-scope`) instead of being denied.
- Keycloak unreachable answers 503 while the enforcer cannot be built (for `retry-backoff` after
  each failed build); once built, a failure of the enforcer answers 503, while a failed
  authorization call is a denial (403), logged at `WARN` and counted.
- Resolved paths are cached (`path-cache.*`). There is still one authorization call per enforced
  request unless the token is an RPT carrying the permission.

## 4.0.0

### Breaking
- Requires `sdk-user-security` 4.0.0, whose configuration classes moved to
  `io.codehunters.security.config` (it is an `api` dependency, so its new packages reach
  consumers). See [sdk-user-security MIGRATION-4.0.md](../sdk-user-security/MIGRATION-4.0.md).
- The filter moved to `io.codehunters.security.authz.servlet`; the auto-configuration, the
  properties and the alias post-processor moved to `io.codehunters.security.authz.config`. See
  [MIGRATION-4.0.md](MIGRATION-4.0.md).

### Changed
- The `keycloak.policy-enforcer.*` and `keycloak.*` aliases are honoured until 5.0.0 (was 4.0.0).

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
