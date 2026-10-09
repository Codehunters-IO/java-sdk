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

## Steps

1. Bump `sdk-user-security` to 5.0.0 (with the policy enforcer, `sdk-user-security-authz` 5.0.0
   too), or the `sdk-bom` that manages them.
2. Search the configuration, Helm values and secrets for `keycloak.security` and
   `KEYCLOAK_SECURITY_`, and rename every hit with the table above.
3. Search the code for casts to `UsernamePasswordAuthenticationToken` of the request's
   authentication and use `KeycloakAuthenticationToken` instead.
