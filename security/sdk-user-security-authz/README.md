# sdk-user-security-authz

Keycloak Authorization Services policy enforcement point (PEP) for services that use
`sdk-user-security`.

```kotlin
implementation("io.codehunters:sdk-user-security-authz:4.0.0")
```

It brings `sdk-user-security` and `keycloak-authz-client`. Services that don't enforce Keycloak
policies should depend on `sdk-user-security` only.

## Behaviour

- Each request whose path is in `paths` is authorized by Keycloak for the resource `name` and the
  scopes of the request's method. Paths not in the catalog pass through.
- The most specific matching pattern decides.
- A method the catalog does not list for a matched path is **denied** (403), so a new route is never
  left unprotected by omission.
- Keycloak denial answers 403; Keycloak unreachable answers 503.
- The Keycloak client is created on the first enforced request, not at startup. Its calls have
  connect and read timeouts; a failed creation is retried only after `retry-backoff`.
- `PERMISSIVE` logs decisions, including missing tokens, and never blocks.
- Keycloak answering 400 (unknown resource or scope) is a catalog mistake: denied and logged as an
  error.
- Runs after Spring Security, so the request is already authenticated.

## Configuration

Keycloak's URL and realm come from `codehunters.security.url` and `codehunters.security.realm`.

| Key | Default | Notes |
|---|---|---|
| `codehunters.security.authz.enabled` | `false` | Opt-in |
| `codehunters.security.authz.enforcement-mode` | `ENFORCING` | `PERMISSIVE` logs and never blocks; `DISABLED` does nothing |
| `codehunters.security.authz.resource` | — | Required when enabled: this service's client id |
| `codehunters.security.authz.client-secret` | — | Required when enabled |
| `codehunters.security.authz.connect-timeout` | `2s` | |
| `codehunters.security.authz.read-timeout` | `3s` | |
| `codehunters.security.authz.retry-backoff` | `30s` | Wait before retrying a failed client creation |
| `codehunters.security.authz.paths[].path` | — | Ant pattern |
| `codehunters.security.authz.paths[].name` | — | Keycloak resource name |
| `codehunters.security.authz.paths[].methods[].method` | — | HTTP method |
| `codehunters.security.authz.paths[].methods[].scopes` | `[]` | Scopes requested for the resource |

```yaml
codehunters:
  security:
    authz:
      enabled: true
      resource: users-service
      client-secret: ${KEYCLOAK_CLIENT_SECRET}
      paths:
        - path: /api/v1/admin/users/**
          name: users
          methods:
            - method: GET
              scopes: [READ]
            - method: POST
              scopes: [CREATE]
```

## Migrating from 3.x

4.0 needs `sdk-user-security` 4.0, whose configuration classes moved to
`io.codehunters.security.config`; this artifact's package and classes do not change. See
[sdk-user-security MIGRATION-4.0.md](../sdk-user-security/MIGRATION-4.0.md).

## Migrating from 2.x (`sdk-user-security` 2.x included the PEP)

| 2.x | 3.0 |
|---|---|
| package `io.codehunters.sdk.security.authz` | `io.codehunters.security.authz` |
| `keycloak.policy-enforcer.*` | `codehunters.security.authz.*` (old keys honoured until 5.0) |
| `keycloak.host`, `keycloak.realm` | `codehunters.security.url`, `codehunters.security.realm` (`keycloak.security.url` wins when both are set) |
| `lazy-load-paths` | Removed (had no effect) |
| `enforcement-mode` ignored | Honoured |
| Unlisted method on a catalogued path passed through | Denied (403) |
| Any Keycloak error answered 403 | Denial 403, outage 503 |
| `AuthzClient` bean created at startup | Created on first use; define your own `AuthzClient` bean to override |
