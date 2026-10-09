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
- Enforcement fails closed. Every authenticated request (request, forward and include dispatches) is
  enforced unless its path is listed in `codehunters.security.authz.skip-paths`; error dispatches
  pass through.
- Keycloak only ever gets the token Spring Security validated, read from the authentication
  (`AbstractOAuth2TokenAuthenticationToken`; sdk-user-security's converter returns a
  `KeycloakAuthenticationToken` of that type so the token survives credential erasure). The request
  is never read again: Keycloak grants from a requesting party token's `authorization.permissions`
  without checking its signature, so a header Spring Security ignored (a forged RPT next to a token
  read from another header, for example) must never reach it. A request authenticated without a
  validated token, or with a token outside the bearer charset, is denied (403).
- Keycloak matches paths on the path as Spring MVC matches it (`RequestPath`, decoded per segment).
  An enforced path holding an encoded `/`, `\`, `.`, `%` or a `;` is denied (403), since its decoded
  form could route to a handler its Keycloak pattern does not cover. A denial Keycloak did not answer
  itself answers 403.
- Anonymous requests are never enforced, so startup fails when a `public-paths` entry covers a
  catalogued path that is not `DISABLED`.
- The first build waits a bounded time (5s) on a lock, then answers 503. A failed enforcer build
  answers 503 for `codehunters.security.authz.retry-backoff` (default 10s); after the window one
  request rebuilds while the others keep getting 503, and a slow rebuild is never duplicated. Enforcer and Keycloak
  call failures are logged at WARN and counted as `codehunters.authz.enforcer.failures`.
- `codehunters.security.authz.*` maps onto Keycloak's `PolicyEnforcerConfig`; startup fails on a
  method mapped without scopes, invalid paths or modes, and missing credentials.
- The enforcer's HTTP client is a bean with explicit timeouts and a bounded pool, closed with the
  context; its path cache is always on.

## Consequences

- Good: Keycloak's own semantics and fixes; enforcement runs after authentication, so a missing or bad
  token is a 401 without a Keycloak call; Keycloak never sees a token Spring Security did not
  validate; misconfigured catalogs and public paths over enforced ones fail at startup.
- Bad: chains that authenticate without a bearer token must list their paths in `skip-paths`, or
  their requests are denied; paths with encoded separators or semicolons are refused when enforced.
- Bad: Keycloak's semantics replace ours (an unmapped path is denied under `ENFORCING`, `PERMISSIVE`
  is not "never block", a Keycloak call that fails after the first build answers 403, because Keycloak
  reports it as a denial — the failure counter tells the two apart); the enforcer caches paths but
  not decisions, so each enforced request still costs one UMA call unless the client sends an RPT;
  Apache HttpClient 4 stays on the classpath because the enforcer requires it.
