# Migrating sdk-user-security-authz 5.x to 6.0

6.0 needs `sdk-user-security` 6.0.0, which rejects a plain `http` `codehunters.security.url`
unless `codehunters.security.allow-insecure-http=true` (see
[its MIGRATION-6.0.md](../sdk-user-security/MIGRATION-6.0.md)). The policy enforcer sends
`codehunters.security.authz.client-secret` to that URL, so the same rule protects it.

Nothing else changes for existing configurations. The enforcer's HTTP client, filter registration,
security customizer and failure counter can now be replaced by defining a bean of the same name
(type, for the counter); see the README.
