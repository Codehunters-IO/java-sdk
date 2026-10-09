# Migrating sdk-user-security-authz 4.x to 5.0

5.0 replaces the in-house policy enforcement point with Keycloak's official policy enforcer
(`keycloak-policy-enforcer`), running inside the Spring Security filter chain. It also stops
reading the 2.x `keycloak.*` keys and needs `sdk-user-security` 5.0.0 (see
[its MIGRATION-5.0.md](../sdk-user-security/MIGRATION-5.0.md)).

## Property renames

An old key left in the configuration is ignored, with no warning: a policy enforcer enabled only
through `keycloak.policy-enforcer.enabled` is now off.

| Old key (no longer read) | Key to use |
|---|---|
| `keycloak.policy-enforcer.enabled` | `codehunters.security.authz.enabled` |
| `keycloak.policy-enforcer.enforcement-mode` | `codehunters.security.authz.enforcement-mode` |
| `keycloak.policy-enforcer.resource` | `codehunters.security.authz.resource` |
| `keycloak.policy-enforcer.client-secret` | `codehunters.security.authz.client-secret` |
| `keycloak.policy-enforcer.paths` | `codehunters.security.authz.paths` |
| `keycloak.host` | `codehunters.security.url` |
| `keycloak.realm` | `codehunters.security.realm` |

## Removed and added properties

| Key | Change |
|---|---|
| `codehunters.security.authz.retry-backoff` | Removed; ignored if left. A failed first build of the enforcer is retried on the next enforced request |
| `codehunters.security.authz.lazy-load-paths` | Added, default `false` |
| `codehunters.security.authz.http-method-as-scope` | Added, default `false` |
| `codehunters.security.authz.path-cache.max-entries` / `lifespan` | Added, default `1000` / `30s` |
| `codehunters.security.authz.claim-information-point.*` | Added |
| `codehunters.security.authz.max-connections` | Added, default `50` |
| `codehunters.security.authz.paths[].enforcement-mode` | Added, default `ENFORCING` |
| `codehunters.security.authz.paths[].scopes` | Added: scopes for methods the path does not list |
| `codehunters.security.authz.paths[].claim-information-point.*` | Added |
| `codehunters.security.authz.paths[].methods[].scopes-enforcement-mode` | Added, default `ALL` |

## Behaviour changes

| 4.x | 5.0 |
|---|---|
| Servlet filter at `LOWEST_PRECEDENCE - 10`, outside Spring Security | Inside every `SecurityFilterChain`, after `AuthorizationFilter` |
| A catalogued path without a token answered 401 from the PEP, public paths included | Spring Security answers 401 on protected paths; anonymous requests on public and excluded paths are not enforced |
| Authenticated requests to paths not in the catalog passed | `ENFORCING` denies them (403). Set `enforcement-mode: PERMISSIVE` to let them pass; mapped paths stay enforced |
| `PERMISSIVE` logged decisions and never blocked | Keycloak's meaning: only unmapped paths pass. Use `paths[].enforcement-mode: DISABLED` to stop enforcing a path |
| Ant patterns (`/api/users/**`) | Keycloak patterns (`/api/users/*` matches any sub-path, `{id}` one segment). `**` fails startup |
| A method not listed for a path was denied | Asks Keycloak for the path's `scopes`, the HTTP method with `http-method-as-scope`, or the bare resource. List every method a path serves |
| A method listed with no scopes was accepted | Startup fails |
| Keycloak unreachable answered 503 | 503 only while the enforcer is first built; afterwards a failed authorization call is a denial (403) |
| Keycloak answering 400 (unknown resource or scope) was logged as an error | Denied (403); the enforcer logs at debug under `org.keycloak.adapters.authorization` |
| One authorization call per enforced request | Same: Keycloak's enforcer has no decision cache. Resolved paths are cached (`path-cache.*`); see the README's Latency section |

## Removed and added classes

| 4.x | 5.0 |
|---|---|
| `io.codehunters.security.authz.servlet.KeycloakAuthzFilter` | `io.codehunters.security.authz.servlet.KeycloakPolicyEnforcerFilter` (extends Keycloak's `ServletPolicyEnforcerFilter`) |
| `keycloakAuthzFilterRegistration` (`FilterRegistrationBean`) | `keycloakPolicyEnforcerFilterRegistration`, disabled: the filter only runs inside the security chain |
| — | `keycloakPolicyEnforcerCustomizer` (`Customizer<HttpSecurity>`) |
| An application `AuthzClient` bean replaced the client | No longer used. Define a `KeycloakPolicyEnforcerFilter` bean to replace the enforcer |
| `KeycloakAuthzLegacyProperties` and its `spring.factories` entry | Removed; nothing replaces it |

The dependency `org.keycloak:keycloak-authz-client` is replaced by
`org.keycloak:keycloak-policy-enforcer` 26.0.12, which brings it.

## Steps

1. Bump `sdk-user-security-authz` and `sdk-user-security` to 5.0.0, or the `sdk-bom` that manages
   them.
2. Search the configuration, Helm values and secrets for `keycloak.policy-enforcer`, `keycloak.host`,
   `keycloak.realm` and their `KEYCLOAK_*` environment forms, and rename every hit with the first
   table.
3. Rewrite `**` patterns as Keycloak patterns, and give every listed method at least one scope.
4. Decide what authenticated requests to unmapped paths should get: keep `ENFORCING` (403) and map
   every route, or set `enforcement-mode: PERMISSIVE` to keep 4.x's pass-through.
5. If you used `PERMISSIVE` to observe without blocking, set `paths[].enforcement-mode: DISABLED`
   on the paths you are not ready to enforce instead.
6. List every method each mapped path serves, or set `paths[].scopes`.
7. Remove `retry-backoff`, and any `AuthzClient` bean defined to customise the old client.
