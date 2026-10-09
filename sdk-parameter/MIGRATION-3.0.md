# Migrating sdk-parameter 2.x to 3.0

3.0 only moves classes to new packages. Behaviour, beans and properties (`codehunters.parameter.*`)
are unchanged; only imports change. In most IDEs, *Optimize/Organize Imports* after replacing the
old packages below fixes every file.

| 2.x | 3.0 |
|---|---|
| `io.codehunters.parameter.application.ParameterClient` | `io.codehunters.parameter.ParameterClient` |
| `io.codehunters.parameter.domain.model.Parameter` | `io.codehunters.parameter.Parameter` |
| `io.codehunters.parameter.domain.model.ParameterModule` | `io.codehunters.parameter.ParameterModule` |
| `io.codehunters.parameter.domain.model.ParameterType` | `io.codehunters.parameter.ParameterType` |
| `io.codehunters.parameter.domain.exception.ParameterException` | `io.codehunters.parameter.ParameterException` |
| `io.codehunters.parameter.domain.exception.ParameterAccessException` | `io.codehunters.parameter.ParameterAccessException` |
| `io.codehunters.parameter.domain.exception.ParameterTypeMismatchException` | `io.codehunters.parameter.ParameterTypeMismatchException` |
| `io.codehunters.parameter.infrastructure.JdbcParameterAdapter` | `io.codehunters.parameter.jdbc.JdbcParameterAdapter` |
| `io.codehunters.parameter.infrastructure.CachingParameterClient` | `io.codehunters.parameter.cache.CachingParameterClient` |

`io.codehunters.parameter.config` (`ParameterAutoConfiguration`, `ParameterProperties`) keeps its
name.

## Logging

Logger categories follow the classes, so a level set on an old package or class name no longer
applies. Rename it the same way as the imports, for example:

```properties
# before
logging.level.io.codehunters.parameter.infrastructure=DEBUG
# after
logging.level.io.codehunters.parameter.jdbc=DEBUG
```

A level on `io.codehunters` keeps working.
