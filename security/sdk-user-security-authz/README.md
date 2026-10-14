# sdk-user-security-authz

Keycloak Authorization Services policy enforcement for services that use `sdk-user-security`.
It wires Keycloak's official policy enforcer (`org.keycloak:keycloak-policy-enforcer`) into the
Spring Security filter chain and configures it from `codehunters.security.authz.*`.

```kotlin
implementation("io.codehunters:sdk-user-security-authz:6.0.0")
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
- **Which requests.** Every authenticated request is enforced (request, forward and include
  dispatches), unless the path the client requested is listed in `skip-paths`; forwards and
  includes are enforced even from a skipped path. The decision fails closed:
  - Keycloak only ever gets the token Spring Security validated, read from the authentication
    (sdk-user-security's `KeycloakAuthenticationToken` or Spring Security's own token
    authentications), wherever the token arrived (an application `BearerTokenResolver` reading a
    query parameter or another header included). The request is never read again: Keycloak grants
    from the permissions of a requesting party token without checking its signature, so a token
    Spring Security did not validate must never reach it.
  - A request authenticated without such a token (basic auth, form login, a custom principal) is
    denied (403). List the paths those chains serve, such as actuator endpoints, in `skip-paths`.
  - A token outside the bearer charset is denied, and so is a denial Keycloak did not answer
    itself.
  - A token that is not a JWT (an opaque token from introspection, for example) is denied (403):
    Keycloak's enforcer parses every token it is given as a JWT. A warning is logged once.
  - A token carrying permissions (an `authorization` claim) is denied unless its audience names
    this resource server (`resource`), as the realm's requesting party tokens do: another client's
    token cannot bring this resource server's permissions with it.
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
  suffix. Ant's `**` is rejected at startup. They are matched against the path as Spring MVC
  matches it, decoded segment by segment: `/api/%6Frders/1` is enforced as `/api/orders/1`. A path
  holding an encoded `/`, `\`, `.` or `%`, or a `;` (encoded or not), could route somewhere its
  decoded form does not match, and an encoded `*`, `{`, `}` or `?` would turn into a pattern
  character for Keycloak's matcher, so an enforced request to it is denied (403). The default Spring
  Security firewall already rejects most of these.
- **Keycloak unreachable.** The enforcer is built on the first enforced request, so the service
  starts without Keycloak; requests arriving meanwhile wait for it up to 5 seconds, then answer
  503. If the build fails, enforced requests answer 503 for `retry-backoff` without calling
  Keycloak; then one request rebuilds while the others keep answering 503, however long the
  rebuild takes. Once built, a failure of the enforcer answers 503, but **a failed authorization
  call is a denial (403)**: Keycloak's enforcer does not tell it apart from a refused permission.
  Clients and alerts see such an outage as 403; watch the `call` stage of
  `codehunters.authz.enforcer.failures` (see Metrics), and every such call is logged at `WARN`.
- **Unknown names.** A resource `name` Keycloak does not know leaves the path without a resource:
  its requests are denied.
- Error dispatches (`/error`) are not enforced a second time; forward and include dispatches are,
  so a public handler forwarding to a protected one cannot bypass the catalog.
- **Paths the enforcer never sees.** Anonymous requests (`public-paths`, `excluded-paths`) and
  skipped ones (`skip-paths`) are never enforced, so startup fails when one of these patterns
  overlaps a catalogued path that is not `DISABLED`, in either direction: `/api/orders/export` in
  `public-paths` overlaps a catalogued `/api/orders/{id}` as much as `/api/**` does. The check is
  conservative (a variable segment matches any segment, whatever its constraint); map public
  routes as `DISABLED` in the catalog (see Public routes) or keep the lists apart.

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

## Metrics

With Micrometer on the classpath and a `MeterRegistry` bean, failures are counted as
`codehunters.authz.enforcer.failures`, tagged `stage`:

| `stage` | Counted when |
|---|---|
| `enforcer` | The enforcer could not be built, or failed while enforcing (both answer 503) |
| `call` | A call to Keycloak failed with an I/O error (connection refused, read timeout); a failed authorization call still answers 403 |

A build that fails because Keycloak is unreachable counts once under each stage.

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
| `codehunters.security.authz.retry-backoff` | `10s` | 503 without retrying after a failed enforcer build |
| `codehunters.security.authz.skip-paths` | `[]` | Spring path patterns never enforced on the client's request, whatever authenticated it (actuator behind basic auth, for example); may not overlap an enforced catalogued path |
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

### Public routes

An authenticated request is enforced on every path, public ones included, so under `ENFORCING` a
caller that sends its token to a public route is denied unless the route is mapped. Map public
routes with `enforcement-mode: DISABLED`; Keycloak is then not asked. `name` is still required and
must be a resource of the client, since the enforcer resolves every mapped path at startup. A
public path covering a catalogued path that is not `DISABLED` fails startup:

```yaml
codehunters:
  security:
    public-paths: [/api/v1/catalog/**, /api/v1/ping]
    authz:
      enabled: true
      resource: catalog-service
      client-secret: ${KEYCLOAK_CLIENT_SECRET}
      paths:
        - path: /api/v1/catalog/*
          name: catalog
          enforcement-mode: DISABLED
        - path: /api/v1/ping
          name: catalog
          enforcement-mode: DISABLED
        - path: /api/v1/orders/{id}
          name: orders
          methods:
            - method: GET
              scopes: [READ]
```

Under `PERMISSIVE`, unmapped routes pass already; the mapping is only needed for public routes
under a mapped pattern.

### Extending

- To read tokens from elsewhere (a query parameter, another header), configure Spring Security's
  `BearerTokenResolver` (a bean or the chain's `oauth2ResourceServer` DSL). The enforcer takes the
  token from the authentication, so it follows whatever Spring Security validated.
- A chain that authenticates without a bearer token (basic auth on actuator, an API key) must list
  its paths in `skip-paths`; otherwise its requests are denied (403).
- Calls to Keycloak go through the `keycloakPolicyEnforcerHttpClient` bean (Apache HttpClient 4),
  closed with the context. It is not a default candidate, so it never becomes the application's
  `HttpClient`. Define a `CloseableHttpClient` bean under that name to replace it (a proxy, mTLS
  towards Keycloak); `keycloakPolicyEnforcerCustomizer`, `keycloakPolicyEnforcerFilterRegistration`
  and the `EnforcerFailureCounter` back off the same way.
- To replace the enforcer, define a `KeycloakPolicyEnforcerFilter` bean; the auto-configuration
  then places yours in the chain. `KeycloakPolicyEnforcerFilter.Options` sets its skipped paths,
  retry backoff, first-build wait, clock and failure listener.

## Migrating from 5.x

6.0 needs `sdk-user-security` 6.0, which rejects a plain `http` Keycloak URL unless
`codehunters.security.allow-insecure-http: true`. See [MIGRATION-6.0.md](MIGRATION-6.0.md).

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
