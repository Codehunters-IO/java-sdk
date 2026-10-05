# sdk-user-security

Request authentication for Spring Boot 4 servlet applications behind Keycloak.

```kotlin
implementation("io.codehunters:sdk-user-security:3.0.0")
```

## Trust model

The access token is the only source of identity and authorities.

1. The bearer token is verified locally against the realm's JWK set: signature, `iss`, `exp`/`nbf`,
   `sub`, and `aud` when `audiences` is set. Unsigned tokens and tokens signed with an unknown key
   are rejected.
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
| `codehunters.security.audiences` | `[]` | Accepted `aud` values; empty accepts any. Set it |
| `codehunters.security.role-clients` | `[]` | Clients whose `resource_access` roles are granted |
| `codehunters.security.public-paths` | `[]` | Anonymous allowed; a token, when sent, must be valid |
| `codehunters.security.excluded-paths` | `[]` | No security at all (health probes) |
| `codehunters.security.cors.*` | see below | `allowed-origins` `[]`, `allowed-methods` GET/POST/PUT/DELETE/PATCH/OPTIONS, `allowed-headers` `*`, `allow-credentials` `false`, `max-age` `3600` |
| `codehunters.security.introspection.enabled` | `true` | Rejects tokens of revoked sessions |
| `codehunters.security.introspection.client-id` | — | Required when introspection is enabled |
| `codehunters.security.introspection.client-secret` | — | Required when introspection is enabled |
| `codehunters.security.introspection.cache-ttl` | `30s` | Also the worst-case revocation latency |
| `codehunters.security.introspection.cache-max-entries` | `10000` | Hard cap; least recently used is evicted |
| `codehunters.security.introspection.fail-open` | `false` | `true` accepts verified tokens while Keycloak is unreachable |
| `codehunters.security.introspection.connect-timeout` | `2s` | |
| `codehunters.security.introspection.read-timeout` | `3s` | |

Missing or invalid required keys fail startup.

## Failure semantics

| Situation | Response |
|---|---|
| No token on a protected path | 401 with `WWW-Authenticate` |
| Invalid, expired, foreign or unsigned token | 401 |
| Session revoked | 401 |
| Keycloak introspection unreachable | 401 (`fail-open: false`) or accepted (`fail-open: true`) |
| `x-user-id` / `x-user-roles` disagree with the token | 401, `application/problem+json` |
| `@PreAuthorize` denies | 403 |

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
| Identity and roles from gateway headers | Identity and roles from the verified token |
| Policy enforcer included | Separate artifact `sdk-user-security-authz` |

Before upgrading, check that the realm grants every role your services authorize on: roles that
only existed in the gateway's `x-user-roles` are no longer granted.
