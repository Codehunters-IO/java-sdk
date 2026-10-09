# Migrating to sdk-commons 2.0

`io.codehunters:sdk-commons` 2.0.0 moves TOTP under the artifact's own package, so every package in
the jar starts with `io.codehunters.commons`. Only imports change: class names, properties
(`codehunters.totp.*`) and behaviour are those of 1.1.0. The
`io.codehunters.commons.*` helpers of 1.x do not move.

| 1.x | 2.0 |
|---|---|
| `io.codehunters.totp.*` (`TotpGenerator`, `TotpSecret`, `TotpConfig`, `TotpAlgorithm`, `TotpDigits`, `TotpException`, `Base32`) | `io.codehunters.commons.totp.*` |
| `io.codehunters.totp.config.TotpAutoConfiguration` | `io.codehunters.commons.totp.config.TotpAutoConfiguration` |
| `io.codehunters.totp.config.TotpProperties` | `io.codehunters.commons.totp.config.TotpProperties` |

1. **Dependency.** Bump `sdk-commons` to 2.0.0, or the `sdk-bom` that manages it.
2. **Imports.** Replace `io.codehunters.totp` with `io.codehunters.commons.totp`. In IntelliJ IDEA,
   *Replace in Files* on the package prefix followed by *Optimize Imports* does it in one pass.
3. **Auto-configuration references.** Code that names `TotpAutoConfiguration` by string
   (`@AutoConfigureAfter(name = ...)`, `excludeName`, `spring.autoconfigure.exclude`) needs the new
   name too.

## Logging

Logger categories follow the classes, so a level set on an old package or class name no longer
applies. Rename it the same way as the imports, for example:

```properties
# before
logging.level.io.codehunters.totp=DEBUG
# after
logging.level.io.codehunters.commons.totp=DEBUG
```

A level on `io.codehunters` keeps working.
