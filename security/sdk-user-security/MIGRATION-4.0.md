# Migrating sdk-user-security 3.x to 4.0

4.0.0 splits the 14 classes of `io.codehunters.security` by concern. What controllers and services
use stays in `io.codehunters.security`; configuration and the Spring Security plumbing move to
sub-packages. Only imports change: class names, properties (`codehunters.security.*`) and behaviour are those of
3.1.0.

## Unchanged: `io.codehunters.security`

`CurrentPrincipal`, `KeycloakPrincipal`, `KeycloakSecurityUtils`, and `Authorities` (package-private
in 3.x, now public).

## Moved

| 3.x | 4.0 |
|---|---|
| `io.codehunters.security.KeycloakSecurityAutoConfiguration` | `io.codehunters.security.config.KeycloakSecurityAutoConfiguration` |
| `io.codehunters.security.KeycloakSecurityProperties` | `io.codehunters.security.config.KeycloakSecurityProperties` |
| `io.codehunters.security.KeycloakJwtAuthenticationConverter` | `io.codehunters.security.jwt.KeycloakJwtAuthenticationConverter` |
| `io.codehunters.security.SessionIntrospector` | `io.codehunters.security.session.SessionIntrospector` |
| `io.codehunters.security.GatewayHeadersFilter` | `io.codehunters.security.gateway.GatewayHeadersFilter` |
| `io.codehunters.security.CurrentPrincipalArgumentResolver` | `io.codehunters.security.mvc.CurrentPrincipalArgumentResolver` |

`ActiveSessionValidator` and `BoundedTtlCache` (package-private in 3.x) moved to
`io.codehunters.security.session`; `ActiveSessionValidator` is now public so the auto-configuration
can reach it.

## Steps

1. **Dependency.** Bump `sdk-user-security` to 4.0.0, or the `sdk-bom` that manages it. With the
   policy enforcer, bump `sdk-user-security-authz` to 4.0.0 as well: 3.x is built against the old
   packages.
2. **Imports.** Update the imports of the classes in the table. In IntelliJ IDEA, delete the stale
   import and let *Optimize Imports* / *Add import* pick the new package.
3. **Names in strings.** `@AutoConfigureAfter(name = ...)`, `excludeName` and
   `spring.autoconfigure.exclude` entries naming `KeycloakSecurityAutoConfiguration` need the
   `config` package.

## Logging

Logger categories follow the classes, so a level set on an old package or class name no longer
applies. Rename it the same way as the imports, for example:

```properties
# before
logging.level.io.codehunters.security.SessionIntrospector=DEBUG
# after
logging.level.io.codehunters.security.session.SessionIntrospector=DEBUG
```

A level on `io.codehunters` keeps working.
