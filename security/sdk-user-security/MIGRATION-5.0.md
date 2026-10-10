# Migrating sdk-user-security 4.x to 5.0

5.0.0 stops reading the 2.x `keycloak.security.*` keys. Configuration lives under
`codehunters.security.*` only; an old key left in the configuration is ignored, with no warning,
and a required setting it used to fill (`url`, `realm`) now fails startup.

## Property renames

| Old key (no longer read) | Key to use |
|---|---|
| `keycloak.security.url` | `codehunters.security.url` |
| `keycloak.security.realm` | `codehunters.security.realm` |
| `keycloak.security.client-id` | `codehunters.security.introspection.client-id` |
| `keycloak.security.client-secret` | `codehunters.security.introspection.client-secret` |
| `keycloak.security.cache-ttl-seconds: 30` | `codehunters.security.introspection.cache-ttl: 30s` |
| `keycloak.security.fail-open` | `codehunters.security.introspection.fail-open` |
| `keycloak.security.public-paths` | `codehunters.security.public-paths` |
| `keycloak.security.excluded-paths` | `codehunters.security.excluded-paths` |
| `keycloak.security.cors.*` | `codehunters.security.cors.*` |

Environment variables follow the same rename: `KEYCLOAK_SECURITY_URL` → `CODEHUNTERS_SECURITY_URL`.

## Removed classes

`io.codehunters.security.config.KeycloakSecurityLegacyProperties` (the environment post-processor
that copied the old keys) and its `spring.factories` entry. Nothing replaces it.

## Session introspection is opt-in

4.x asked Keycloak about every uncached token by default and rejected the request when Keycloak
could not answer (`fail-open: false`), so a Keycloak outage became an outage of every service once
the cache expired. 5.0.0 does not introspect unless told to:

| | 4.x | 5.0 |
|---|---|---|
| `codehunters.security.introspection.enabled` | `true` | `false` |
| Token of a revoked session | 401 | accepted until it expires |
| Keycloak down | 401 after `cache-ttl` | no effect |
| `client-id` / `client-secret` | required | required only when enabled |

To keep 4.x's behaviour, set it explicitly:

```yaml
codehunters:
  security:
    introspection:
      enabled: true
      client-id: my-service
      client-secret: ${KEYCLOAK_CLIENT_SECRET}
```

Leaving it off is the right choice when access tokens are short-lived (minutes) and a revoked
session may keep working until its token expires. Credentials left in the configuration without
`enabled: true` are ignored, and startup logs a `WARN` naming the gap ("introspection credentials
configured but introspection is disabled"); add `enabled: true` or remove the credentials to clear
it. The result cache is now Caffeine (same `cache-ttl` and
`cache-max-entries`), and its hits, misses and failures are published as Micrometer meters; see
the README's "Session introspection".

## Behaviour

5.0.0 also carries the principal changes listed in the [CHANGELOG](CHANGELOG.md):
`Authentication.getName()` is the user id, a token without `sub` answers 401, and the
gateway-header 401 goes through the application's `AuthenticationEntryPoint`.

The authentication of a bearer request is now a `KeycloakAuthenticationToken`
(`AbstractOAuth2TokenAuthenticationToken<Jwt>`) instead of a `UsernamePasswordAuthenticationToken`,
so the validated token survives credential erasure. The principal is still the
`KeycloakPrincipal`; only code that casts the authentication itself changes:

| 4.x | 5.0 |
|---|---|
| `(UsernamePasswordAuthenticationToken) authentication` | `(KeycloakAuthenticationToken) authentication` |
| `authentication.getCredentials()` held the `Jwt` until erased | `((KeycloakAuthenticationToken) authentication).getToken()`, always |

Only Keycloak access tokens (`typ` = `Bearer`) are accepted. A client that sent an ID token whose
audience matched now gets 401; send the access token, or set
`codehunters.security.require-access-token-type=false`.

`KeycloakAuthenticationToken` marks itself authenticated: build it only from a `Jwt` a `JwtDecoder`
verified, normally through `KeycloakJwtAuthenticationConverter`. Keycloak's policy enforcer trusts
the permissions such a token carries.

The SDK's filter chains are stateless. An application that stores the security context in a JSON
Spring Session on its own chain must register Jackson mixins for `KeycloakAuthenticationToken` and
`KeycloakPrincipal`, which Spring Security's allowlist does not cover; 4.x had the same requirement
for `KeycloakPrincipal`.

## Steps

1. Bump `sdk-user-security` to 5.0.0 (with the policy enforcer, `sdk-user-security-authz` 5.0.0
   too), or the `sdk-bom` that manages them.
2. Search the configuration, Helm values and secrets for `keycloak.security` and
   `KEYCLOAK_SECURITY_`, and rename every hit with the table above.
3. Search the code for casts to `UsernamePasswordAuthenticationToken` of the request's
   authentication and use `KeycloakAuthenticationToken` instead.
4. Decide on introspection: services that set `introspection.client-id` relied on it being on.
   Add `codehunters.security.introspection.enabled: true` to keep it, or remove the client
   credentials.
