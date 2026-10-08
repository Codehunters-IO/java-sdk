# sdk-user-security

Request authentication for Spring Boot 4 servlet applications behind Keycloak.

```kotlin
implementation("io.codehunters:sdk-user-security:3.0.0")
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
5. `x-geo-*` and `User-Agent` are added to the principal as context. They are not verified; never
   base an authorization decision on them.

A caller that reaches the pod without the gateway gets nothing a valid token would not already give
it.

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
| `x-user-id` / `x-user-roles` disagree with the token | 401, `application/problem+json` |
| `@PreAuthorize` denies | 403 |

With `sdk-web` (its errors part) on the classpath, 401 and 403 bodies are `application/problem+json` with
`code` and `traceId`, and the 401 keeps the bearer `WWW-Authenticate` challenge. Any application
that defines exactly one `AuthenticationEntryPoint` and one `AccessDeniedHandler` bean gets the same
wiring with its own handlers. Without them the bodies are empty, as before.

## Extension points

Every bean backs off when the application defines its own: `JwtDecoder`,
`KeycloakJwtAuthenticationConverter`, `SessionIntrospector`. Defining any `SecurityFilterChain`
replaces both SDK chains; wire `GatewayHeadersFilter` after `BearerTokenAuthenticationFilter`
yourself if you still want the header checks.

## Migrating from 2.x

| 2.x | 3.0 |
|---|---|
| package `io.codehunters.sdk.security` | `io.codehunters.security` |
| `keycloak.security.*` | `codehunters.security.*` (old keys honoured until 4.0, with a warning) |
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
