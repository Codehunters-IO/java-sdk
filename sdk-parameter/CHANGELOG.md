# Changelog — sdk-parameter

## 2.0.0

### Breaking
- `ParameterModule` is a record (`ParameterModule.of(code)`, `code()`) instead of an enum. The
  constants keep their names; `valueOf`, `values()`, `name()`, `ordinal()` and `switch` over it are
  gone. Rows with a module unknown to the SDK now map instead of returning `null`.
- `asBoolean()` accepts only `true` or `false`; other values throw
  `ParameterTypeMismatchException` (1.x returned `false`).
- `asInteger()` out of range throws `ParameterTypeMismatchException` (was `ArithmeticException`).
- Typed accessors on a `null` value throw `ParameterTypeMismatchException` (was
  `NullPointerException`).
- `ParameterTypeMismatchException(ParameterType, String value, Throwable)` is replaced by
  `ParameterTypeMismatchException(ParameterType, String parameterName)`; messages no longer
  include the value, and parsing errors carry no cause.
- `asMap` and `asMapByName` throw `ParameterException` when two rows share a name (1.x kept one of
  them), keep the name order and accept `null` values.
- A blank submodule means the empty submodule.
- `parameterJdbcClient` is not an autowiring candidate.
- The auto-configuration needs a single `DataSource` candidate.

### Added
- `codehunters.parameter.enabled`, `table-name` and `cache-ttl`.
- `CachingParameterClient`, a bounded TTL cache used when `cache-ttl` is positive.
- `JdbcParameterAdapter(JdbcClient, String tableName)`.

### Removed
- Lombok, and the `compileOnly` dependency on `spring-boot-starter-jdbc`.
