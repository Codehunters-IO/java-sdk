# Migrating to sdk-web 2.0

`io.codehunters:sdk-web` 2.0.0 moves its three parts under the artifact's own package,
`io.codehunters.web`, and splits the errors adapter by what each class does. Only imports change:
class names, properties (`codehunters.web-errors.*`, `codehunters.device.*`, `codehunters.i18n.*`),
the legacy `vitxo.i18n.*` aliases and behaviour are those of 1.0.0.

## Packages

| 1.x | 2.0 |
|---|---|
| `io.codehunters.weberrors` (`ApiException`, `ErrorCode`, `CommonErrorCode`, `FieldViolation`) | `io.codehunters.web.errors` |
| `io.codehunters.weberrors.config` | `io.codehunters.web.errors.config` |
| `io.codehunters.weberrors.web` | split, see below |
| `io.codehunters.device` | `io.codehunters.web.device` |
| `io.codehunters.device.{config,geo,ip,servlet,uap,useragent}` | `io.codehunters.web.device.{config,geo,ip,servlet,uap,useragent}` |
| `io.codehunters.i18n` | `io.codehunters.web.i18n` |
| `io.codehunters.i18n.config` | `io.codehunters.web.i18n.config` |

## Classes of `io.codehunters.weberrors.web`

| 1.x | 2.0 |
|---|---|
| `io.codehunters.weberrors.web.WebErrorsExceptionHandler` | `io.codehunters.web.errors.servlet.WebErrorsExceptionHandler` |
| `io.codehunters.weberrors.web.ProblemErrorController` | `io.codehunters.web.errors.servlet.ProblemErrorController` |
| `io.codehunters.weberrors.web.ProblemWriter` | `io.codehunters.web.errors.servlet.ProblemWriter` |
| `io.codehunters.weberrors.web.ProblemSettings` | `io.codehunters.web.errors.servlet.ProblemSettings` |
| `io.codehunters.weberrors.web.ProblemAuthenticationEntryPoint` | `io.codehunters.web.errors.security.ProblemAuthenticationEntryPoint` |
| `io.codehunters.weberrors.web.ProblemAccessDeniedHandler` | `io.codehunters.web.errors.security.ProblemAccessDeniedHandler` |
| `io.codehunters.weberrors.web.TraceIdProvider` | `io.codehunters.web.errors.trace.TraceIdProvider` |
| `io.codehunters.weberrors.web.MdcTraceIdProvider` | `io.codehunters.web.errors.trace.MdcTraceIdProvider` |

## Auto-configurations

| 1.x | 2.0 |
|---|---|
| `io.codehunters.weberrors.config.WebErrorsAutoConfiguration` | `io.codehunters.web.errors.config.WebErrorsAutoConfiguration` |
| `io.codehunters.device.config.DeviceAutoConfiguration` | `io.codehunters.web.device.config.DeviceAutoConfiguration` |
| `io.codehunters.i18n.config.I18nAutoConfiguration` | `io.codehunters.web.i18n.config.I18nAutoConfiguration` |
| `io.codehunters.i18n.config.I18nLegacyProperties` | `io.codehunters.web.i18n.config.I18nLegacyProperties` |

## Steps

1. **Dependency.** Bump `sdk-web` to 2.0.0, or the `sdk-bom` that manages it. Libraries built
   against sdk-web 1.x classes (for example `sdk-audit-log` 3.x, which names the device classes)
   need their release that matches 2.0.
2. **Imports.** Replace `io.codehunters.weberrors.web.` with the package from the class table,
   then the prefixes `io.codehunters.weberrors` → `io.codehunters.web.errors`,
   `io.codehunters.device` → `io.codehunters.web.device` and `io.codehunters.i18n` →
   `io.codehunters.web.i18n`. In IntelliJ IDEA, *Replace in Files* on each prefix followed by
   *Optimize Imports* does it.
3. **Names in strings.** `@AutoConfigureAfter(name = ...)`, `excludeName`,
   `spring.autoconfigure.exclude` and `FilteredClassLoader` prefixes that name these classes need
   the new names too.

The `vitxo.i18n.*` keys are now honoured until 3.0.0 (was 2.0.0).

## Logging

Logger categories follow the classes, so a level set on an old package or class name no longer
applies. Rename it the same way as the imports, for example:

```properties
# before
logging.level.io.codehunters.weberrors=DEBUG
# after
logging.level.io.codehunters.web.errors=DEBUG
```

A level on `io.codehunters` keeps working.
