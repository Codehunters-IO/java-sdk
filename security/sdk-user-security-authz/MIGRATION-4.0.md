# Migrating sdk-user-security-authz 3.x to 4.0

4.0 moves this artifact's classes out of the single `io.codehunters.security.authz` package
([ADR 0020](../../docs/adr/0020-package-by-capability.md)) and needs `sdk-user-security` 4.0, whose
own packages also moved (see [its MIGRATION-4.0.md](../sdk-user-security/MIGRATION-4.0.md)).
Only imports change: property names and behaviour are the same. In most IDEs, "organise imports"
after the version bump resolves every reference.

| 3.x | 4.0 |
|---|---|
| `io.codehunters.security.authz.KeycloakAuthzFilter` | `io.codehunters.security.authz.servlet.KeycloakAuthzFilter` |
| `io.codehunters.security.authz.KeycloakAuthzAutoConfiguration` | `io.codehunters.security.authz.config.KeycloakAuthzAutoConfiguration` |
| `io.codehunters.security.authz.KeycloakPolicyEnforcerProperties` | `io.codehunters.security.authz.config.KeycloakPolicyEnforcerProperties` |

- An `exclude` or `afterName` that names the auto-configuration must use its new name.
- Logger categories moved with the classes: `logging.level.io.codehunters.security.authz` still
  covers both, but a level set on a class name must use the new one, for example
  `logging.level.io.codehunters.security.authz.servlet.KeycloakAuthzFilter`.
