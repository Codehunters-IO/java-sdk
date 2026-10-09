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

- The module becomes a thin auto-configuration around Keycloak's `PolicyEnforcer`. Its own servlet
  `Filter` (it does not extend `ServletPolicyEnforcerFilter`, whose synchronized build would queue
  every request during an outage) is added to the Spring Security chain right after
  `AuthorizationFilter` through a `Customizer<HttpSecurity>` bean.
- Enforcement is decided by the authentication, not by the raw header: a request is enforced when it
  was authenticated from an OAuth2 token (`AbstractOAuth2TokenAuthenticationToken`, OAuth2 token
  credentials or a `KeycloakPrincipal`). The token comes from the authentication, else from the
  application's `BearerTokenResolver`, else the default resolver; a token-based authentication whose
  token cannot be found is denied (403). Other chains (basic auth on actuator, for example) and error
  dispatches pass through.
- Keycloak matches paths on the decoded, normalised request path (a request wrapper), so an encoded
  path cannot miss its mapping.
- A failed enforcer build answers 503 for `codehunters.security.authz.retry-backoff` (default 10s);
  after the window one request rebuilds while the others keep getting 503. Enforcer and Keycloak
  call failures are logged at WARN and counted as `codehunters.authz.enforcer.failures`.
- `codehunters.security.authz.*` maps onto Keycloak's `PolicyEnforcerConfig`; startup fails on a
  method mapped without scopes, invalid paths or modes, and missing credentials.
- The enforcer's HTTP client is a bean with explicit timeouts and a bounded pool, closed with the
  context; its path cache is always on.

## Consequences

- Good: Keycloak's own semantics and fixes; enforcement runs after authentication, so a missing or bad
  token is a 401 without a Keycloak call; misconfigured catalogs fail at startup.
- Bad: Keycloak's semantics replace ours (an unmapped path is denied under `ENFORCING`, `PERMISSIVE`
  is not "never block", a Keycloak call that fails after the first build answers 403, because Keycloak
  reports it as a denial — the failure counter tells the two apart); the enforcer caches paths but
  not decisions, so each enforced request still costs one UMA call unless the client sends an RPT;
  Apache HttpClient 4 stays on the classpath because the enforcer requires it.
