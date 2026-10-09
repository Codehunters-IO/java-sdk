# sdk-user-security-authz

Keycloak Authorization Services policy enforcement for services that use `sdk-user-security`.
It wires Keycloak's official policy enforcer (`org.keycloak:keycloak-policy-enforcer`) into the
Spring Security filter chain and configures it from `codehunters.security.authz.*`.

```kotlin
implementation("io.codehunters:sdk-user-security-authz:5.0.0")
```

It brings `sdk-user-security` and `keycloak-policy-enforcer` (with its `keycloak-authz-client` and
Apache HttpClient 4). Services that don't enforce Keycloak policies should depend on
`sdk-user-security` only.

## Behaviour

Decisions are Keycloak's enforcer's; this module only configures it and places it in the chain.

- **Placement.** The enforcer runs right after Spring Security's `AuthorizationFilter`, in every
  `SecurityFilterChain`: the sdk-user-security chains and any chain the application defines. It
  is added by a `Customizer<HttpSecurity>` bean, which Spring Security applies to each
  `HttpSecurity` it builds.
- **Authentication first.** A protected path without a valid token answers 401 from Spring
  Security; Keycloak is not asked. Requests Spring Security lets through unauthenticated (public
  and excluded paths) are not enforced. An authenticated request is enforced on every path,
  including public ones.
- **Mapped paths.** The resource `name` and the scopes of the request's method are asked of
  Keycloak; a denial answers 403.
- **Unlisted methods.** A method a path does not list asks for the path's `scopes` (any of them),
  or for the HTTP method when `http-method-as-scope` is on. With neither, Keycloak is asked for the
  resource alone, which it grants to anyone with a permission on it: list every method the path
  serves.
- **Unmapped paths.** `ENFORCING` (the default) denies them with 403; `PERMISSIVE` lets them
  through. Mapped paths follow their own `enforcement-mode` in both cases. `DISABLED` evaluates
  nothing.
- **Patterns** are Keycloak's: `/*` at the end matches any sub-path, `{id}` one segment, `*.ext` a
  suffix. Ant's `**` is rejected at startup.
- **Keycloak unreachable.** The enforcer is built on the first enforced request, so the service
  starts without Keycloak. If Keycloak cannot be reached then, the request answers 503 and the next
  enforced request tries again. Once built, a failed authorization call counts as a denial (403).
- **Unknown names.** A resource `name` Keycloak does not know leaves the path without a resource:
  its requests are denied.
- Error dispatches (`/error`) are not enforced a second time.

## Latency

Keycloak's enforcer has no decision cache.

| When | Calls to Keycloak |
|---|---|
| First enforced request | UMA discovery, a service-account token, and one resource lookup per mapped path (every resource of the client when `paths` is empty). Other requests wait meanwhile. |
| Each enforced request | One UMA grant call to the token endpoint, unless the bearer token is an RPT that already carries the permission. |
| First request to a new URI under a `/*` or `{id}` pattern | Up to three resource lookups by URI, then cached per URI for `path-cache.lifespan`. |

Every call is bounded by `connect-timeout` and `read-timeout`, and at most `max-connections` run at
once. Map only the routes that need resource-level decisions; coarse role checks are cheaper with
`@PreAuthorize`.

## Configuration

Keycloak's URL and realm come from `codehunters.security.url` and `codehunters.security.realm`.

| Key | Default | Notes |
|---|---|---|
| `codehunters.security.authz.enabled` | `false` | Opt-in |
| `codehunters.security.authz.enforcement-mode` | `ENFORCING` | `ENFORCING`, `PERMISSIVE` or `DISABLED`; see Behaviour |
| `codehunters.security.authz.resource` | — | Required when enabled: this service's client id |
| `codehunters.security.authz.client-secret` | — | Required when enabled |
| `codehunters.security.authz.lazy-load-paths` | `false` | Resolve resources by URI on demand |
| `codehunters.security.authz.http-method-as-scope` | `false` | Use the HTTP method as the scope of unlisted methods |
| `codehunters.security.authz.path-cache.max-entries` | `1000` | Paths kept in the enforcer's cache |
| `codehunters.security.authz.path-cache.lifespan` | `30s` | How long a cached path is reused |
| `codehunters.security.authz.claim-information-point.*` | — | Keycloak claim information point for every path |
| `codehunters.security.authz.connect-timeout` | `2s` | |
| `codehunters.security.authz.read-timeout` | `3s` | |
| `codehunters.security.authz.max-connections` | `50` | Concurrent connections to Keycloak |
| `codehunters.security.authz.paths[].path` | — | Keycloak pattern; must start with `/` |
| `codehunters.security.authz.paths[].name` | — | Keycloak resource name; required |
| `codehunters.security.authz.paths[].enforcement-mode` | `ENFORCING` | `DISABLED` skips the path |
| `codehunters.security.authz.paths[].scopes` | `[]` | Scopes for methods the path does not list |
| `codehunters.security.authz.paths[].claim-information-point.*` | — | Claim information point for this path |
| `codehunters.security.authz.paths[].methods[].method` | — | HTTP method, any case |
| `codehunters.security.authz.paths[].methods[].scopes` | — | At least one; startup fails otherwise |
| `codehunters.security.authz.paths[].methods[].scopes-enforcement-mode` | `ALL` | `ALL` or `ANY` |

```yaml
codehunters:
  security:
    authz:
      enabled: true
      resource: users-service
      client-secret: ${KEYCLOAK_CLIENT_SECRET}
      paths:
        - path: /api/v1/admin/users/*
          name: users
          methods:
            - method: GET
              scopes: [READ]
            - method: POST
              scopes: [CREATE]
        - path: /api/v1/status
          name: status
          enforcement-mode: DISABLED
```

To replace the enforcer, define a `KeycloakPolicyEnforcerFilter` bean; the auto-configuration
then places yours in the chain.

## Migrating from 4.x

5.0 replaces the in-house enforcer with Keycloak's, inside the Spring Security chain, and no
longer reads the `keycloak.policy-enforcer.*`, `keycloak.host` and `keycloak.realm` keys. Patterns,
unmapped paths, `PERMISSIVE` and outage answers behave differently. See
[MIGRATION-5.0.md](MIGRATION-5.0.md).

## Migrating from 3.x

4.0 moves the filter to `io.codehunters.security.authz.servlet` and the auto-configuration and
properties to `io.codehunters.security.authz.config`, and needs `sdk-user-security` 4.0, whose
packages also moved. See [MIGRATION-4.0.md](MIGRATION-4.0.md).

## Migrating from 2.x (`sdk-user-security` 2.x included the PEP)

| 2.x | 3.0 |
|---|---|
| package `io.codehunters.sdk.security.authz` | `io.codehunters.security.authz` |
| `keycloak.policy-enforcer.*` | `codehunters.security.authz.*` (old keys are not read since 5.0) |
| `keycloak.host`, `keycloak.realm` | `codehunters.security.url`, `codehunters.security.realm` (old keys are not read since 5.0) |
| `lazy-load-paths` | Removed (had no effect); back in 5.0 as Keycloak's setting |
| `enforcement-mode` ignored | Honoured |
| Unlisted method on a catalogued path passed through | Denied (403) in 3.x and 4.x; Keycloak's rule since 5.0 |
| Any Keycloak error answered 403 | Denial 403, outage 503 in 3.x and 4.x; see 5.0 |
| `AuthzClient` bean created at startup | Created on first use; replaced by the enforcer in 5.0 |
