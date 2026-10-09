# sdk-user-security

Request authentication for Spring Boot 4 servlet applications behind Keycloak.

```kotlin
implementation("io.codehunters:sdk-user-security:5.0.0")
```

## Trust model

The access token is the only source of identity and authorities.

1. The bearer token is verified locally against the realm's JWK set: RS256 signature, `iss`,
   `exp`/`nbf` (60 s skew), `sub` and `aud`. Unsigned tokens, HS256 tokens and tokens signed with an
   unknown key are rejected. The key set is cached and refetched at most every 30 s, so tokens with
   made-up key ids cannot make the service hammer Keycloak.
2. Unless introspection is disabled, Keycloak is asked whether the token's session is still active
   (RFC 7662). This runs only for tokens that passed step 1.
3. Authorities are the realm roles plus the roles of the clients listed in `role-clients`, as
   `ROLE_<NAME>`.
4. The gateway's `x-user-id` and `x-user-roles` headers are optional. When present they must agree
   with the token: another user id, or a role the token does not grant, answers 401. Identity
   headers without a token answer 401.
5. `x-geo-*` and `User-Agent` are added to the principal as a `GatewayContext`. **They are
   client-supplied and not verified**: see below.

A caller that reaches the pod without the gateway gets nothing a valid token would not already give
it.

### What is verified and what is not

| Value | Source | Verified | Read it with |
|---|---|---|---|
| User id, username, session id | Token (`sub`, `preferred_username`, `sid`) | Yes | `KeycloakSecurityUtils.getCurrentUserId()` / `getCurrentUsername()` / `getCurrentSessionId()` |
| Roles | Token | Yes | `KeycloakSecurityUtils.getCurrentRoles()` / `hasRole(...)` |
| Country, city, coordinates, IP, User-Agent | Gateway headers (`x-geo-*`, `User-Agent`) | **No** | `KeycloakSecurityUtils.getGatewayContext()` |

Any caller that bypasses the gateway can set the gateway headers to anything, so `GatewayContext` is
for display and analytics only: never authorization, rate limiting, fraud checks or audit trails.
For a client IP you can trust, use `sdk-web`'s `ClientContextResolver`, which honours forwarding
headers only from configured trusted proxies. The 4.0 getters `getCurrentIp()`,
`getCurrentCountry()`, `getCurrentCity()`, `getCurrentLatitude()`, `getCurrentLongitude()` and the
matching `KeycloakPrincipal` accessors still work but are deprecated for removal:

```java
// before
KeycloakSecurityUtils.getCurrentIp();
// after: same unverified value, now named as such
KeycloakSecurityUtils.getGatewayContext().map(GatewayContext::ip);
```

### Principal name

`KeycloakPrincipal` is an `AuthenticatedPrincipal` named after the user id, so
`Authentication.getName()`, `HttpServletRequest.getRemoteUser()` and
`@PreAuthorize("authentication.name == #id")` see the user id. Its `toString()` prints the user id
only, and `GatewayContext.toString()` prints no value at all, so neither leaks the gateway context
or the session id into logs or security events.

That redaction covers `toString()` only. Serialized as JSON (a session store, a cache, an event
payload), `KeycloakPrincipal` still writes every component, the deprecated and unverified gateway
values included, exactly as in 4.0.0; `getName()` is not written, so stored principals read back
with unknown properties failing. Do not serialize the principal where those values must not go.

A principal always has a user id: the record refuses a null or blank one, and the token converter
rejects a token without `sub` as an invalid bearer token (401).

## Configuration

| Key | Default | Notes |
|---|---|---|
| `codehunters.security.enabled` | `true` | `false` registers nothing |
| `codehunters.security.url` | — | **Required.** Keycloak base URL, `http(s)://…` |
| `codehunters.security.realm` | — | **Required** |
| `codehunters.security.issuer-uri` | `<url>/realms/<realm>` | Set when the service reaches Keycloak through another host than the one in the tokens' `iss` |
| `codehunters.security.jwk-set-uri` | `<url>/realms/<realm>/protocol/openid-connect/certs` | |
| `codehunters.security.audiences` | — | **Required**: accepted `aud` values, normally this service's client id |
| `codehunters.security.allow-any-audience` | `false` | `true` accepts tokens issued to any client of the realm; avoid in shared realms |
| `codehunters.security.require-access-token-type` | `true` | Accept only tokens with `typ` = `Bearer` (Keycloak access tokens); ID and refresh tokens of the realm are rejected |
| `codehunters.security.role-clients` | `[]` | Clients whose `resource_access` roles are granted |
| `codehunters.security.public-paths` | `[]` | Anonymous allowed; a token, when sent, must be valid |
| `codehunters.security.excluded-paths` | `[]` | No security at all (health probes) |
| `codehunters.security.cors.*` | see below | `allowed-origins` `[]`, `allowed-methods` GET/POST/PUT/DELETE/PATCH/OPTIONS, `allowed-headers` `*`, `allow-credentials` `false`, `max-age` `3600` |
| `codehunters.security.method-security.enabled` | `true` | `false` leaves `@EnableMethodSecurity` to the application |
| `codehunters.security.introspection.enabled` | `true` | Rejects tokens of revoked sessions |
| `codehunters.security.introspection.client-id` | — | Required when introspection is enabled |
| `codehunters.security.introspection.client-secret` | — | Required when introspection is enabled |
| `codehunters.security.introspection.cache-ttl` | `30s` | Also the worst-case revocation latency |
| `codehunters.security.introspection.cache-max-entries` | `10000` | Hard cap; least recently used is evicted |
| `codehunters.security.introspection.fail-open` | `false` | `true` accepts verified tokens while Keycloak is unreachable |
| `codehunters.security.introspection.connect-timeout` | `2s` | |
| `codehunters.security.introspection.read-timeout` | `3s` | |

Missing or invalid required keys fail startup. Path patterns use Spring's `PathPattern` syntax:
`**` is only allowed at the end (`/api/**`), unlike 2.x's Ant patterns (`/api/**/health`).

## Failure semantics

| Situation | Response |
|---|---|
| No token on a protected path | 401 with `WWW-Authenticate` |
| Invalid, expired, foreign or unsigned token | 401 |
| Session revoked | 401 |
| Keycloak introspection unreachable | 401 (`fail-open: false`) or accepted (`fail-open: true`) |
| `x-user-id` / `x-user-roles` disagree with the token, or come without a token | 401, `application/problem+json` |
| `@PreAuthorize` denies | 403 |

With `sdk-web` (its errors part) on the classpath, 401 and 403 bodies are `application/problem+json` with
`code` and `traceId`, and the 401 keeps the bearer `WWW-Authenticate` challenge. Any application
that defines exactly one `AuthenticationEntryPoint` and one `AccessDeniedHandler` bean, or marks
one of several `@Primary`, gets the same wiring with its own handlers. With several and no primary,
Spring's defaults answer and a warning at startup says so. Without them the bodies are empty, as
before, except for the
gateway-header 401, which always carries a problem detail (`type`, `title`, `status`, `detail`,
`instance`, `code: UNAUTHORIZED`) and the bearer challenge.

## Extension points

Every bean backs off when the application defines its own: `JwtDecoder`,
`KeycloakJwtAuthenticationConverter`, `SessionIntrospector`. Defining any `SecurityFilterChain`
replaces both SDK chains; wire `GatewayHeadersFilter` after `BearerTokenAuthenticationFilter`
yourself if you still want the header checks, passing your `AuthenticationEntryPoint` to its
constructor so its 401 matches the others.

## Migrating from 4.x

5.0 no longer reads the `keycloak.security.*` keys; use `codehunters.security.*`. See
[MIGRATION-5.0.md](MIGRATION-5.0.md).

## Migrating from 3.x

4.0 moves configuration and Spring Security plumbing out of `io.codehunters.security` into
`config`, `jwt`, `session`, `gateway` and `mvc` sub-packages. Only imports change; see
[MIGRATION-4.0.md](MIGRATION-4.0.md).

## Migrating from 2.x

| 2.x | 3.0 |
|---|---|
| package `io.codehunters.sdk.security` | `io.codehunters.security` |
| `keycloak.security.*` | `codehunters.security.*` (old keys are not read since 5.0) |
| `keycloak.security.client-id` / `client-secret` | `codehunters.security.introspection.client-id` / `client-secret` |
| `keycloak.security.cache-ttl-seconds: 30` | `codehunters.security.introspection.cache-ttl: 30s` |
| `keycloak.security.fail-open` (default `true`) | `codehunters.security.introspection.fail-open` (default `false`) |
| Active only in `develop`, `cert`, `prod` profiles | Active unless `codehunters.security.enabled=false` |
| No audience check | `codehunters.security.audiences` required |
| Ant path patterns (`/api/**/health`) | `PathPattern` (`**` only at the end); invalid patterns fail startup |
| Identity and roles from gateway headers | Identity and roles from the verified token |
| Policy enforcer included | Separate artifact `sdk-user-security-authz` |

Before upgrading, check that the realm grants every role your services authorize on: roles that
only existed in the gateway's `x-user-roles` are no longer granted.
