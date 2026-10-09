# Migrating sdk-user-security-authz 4.x to 5.0

5.0.0 stops reading the 2.x `keycloak.*` keys and needs `sdk-user-security` 5.0.0 (see
[its MIGRATION-5.0.md](../sdk-user-security/MIGRATION-5.0.md)). An old key left in the
configuration is ignored, with no warning: a policy enforcer enabled only through
`keycloak.policy-enforcer.enabled` is now off.

## Property renames

| Old key (no longer read) | Key to use |
|---|---|
| `keycloak.policy-enforcer.enabled` | `codehunters.security.authz.enabled` |
| `keycloak.policy-enforcer.enforcement-mode` | `codehunters.security.authz.enforcement-mode` |
| `keycloak.policy-enforcer.resource` | `codehunters.security.authz.resource` |
| `keycloak.policy-enforcer.client-secret` | `codehunters.security.authz.client-secret` |
| `keycloak.policy-enforcer.paths` | `codehunters.security.authz.paths` |
| `keycloak.host` | `codehunters.security.url` |
| `keycloak.realm` | `codehunters.security.realm` |

## Removed classes

`io.codehunters.security.authz.config.KeycloakAuthzLegacyProperties` and its `spring.factories`
entry. Nothing replaces it.

## Steps

1. Bump `sdk-user-security-authz` and `sdk-user-security` to 5.0.0, or the `sdk-bom` that manages
   them.
2. Search the configuration, Helm values and secrets for `keycloak.policy-enforcer`, `keycloak.host`,
   `keycloak.realm` and their `KEYCLOAK_*` environment forms, and rename every hit with the table
   above.
