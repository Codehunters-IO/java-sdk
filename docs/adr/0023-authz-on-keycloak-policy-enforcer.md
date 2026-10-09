# 0023 — sdk-user-security-authz on Keycloak's official policy enforcer

- Status: accepted
- Date: 2026-10-09
- Supersedes: the adapter technology of [0002](0002-move-policy-enforcer-to-its-own-artifact.md)
  (the separate artifact stays)

## Context

`sdk-user-security-authz` re-implemented a subset of Keycloak's policy enforcer on
`keycloak-authz-client`: a servlet filter outside the Spring Security chain that re-parsed the bearer
header, made one synchronous UMA call per request with no caching, and could allow a method mapped
with empty scopes. Keycloak publishes `org.keycloak:keycloak-policy-enforcer`, a Jakarta servlet
enforcer with path and method mapping, enforcement modes, lazy path loading, claim information points
and a path cache.

## Decision

- The module becomes a thin auto-configuration around Keycloak's `ServletPolicyEnforcerFilter`,
  added to the Spring Security chain right after `AuthorizationFilter` through a
  `Customizer<HttpSecurity>` bean. Only authenticated requests carrying a `Bearer` token, on normal
  (non-error) dispatches, are enforced; other chains (basic auth on actuator, for example) pass
  through.
- `codehunters.security.authz.*` maps onto Keycloak's `PolicyEnforcerConfig`; startup fails on a
  method mapped without scopes, invalid paths or modes, and missing credentials.
- The enforcer's HTTP client gets explicit timeouts and a bounded pool; its path cache is always on.

## Consequences

- Good: Keycloak's own semantics and fixes; enforcement runs after authentication, so a missing or bad
  token is a 401 without a Keycloak call; misconfigured catalogs fail at startup.
- Bad: Keycloak's semantics replace ours (an unmapped path is denied under `ENFORCING`, `PERMISSIVE`
  is not "never block", an outage after the first build answers 403); the enforcer caches paths but
  not decisions, so each enforced request still costs one UMA call unless the client sends an RPT;
  Apache HttpClient 4 stays on the classpath because the enforcer requires it.
