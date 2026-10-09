# Changelog — sdk-parameter

## 3.0.0

### Breaking
- Packages reorganised by purpose instead of by layer; only imports change, properties and beans
  are unchanged. The API (`ParameterClient`, `Parameter`, `ParameterModule`, `ParameterType`,
  exceptions) is in `io.codehunters.parameter`, `JdbcParameterAdapter` in
  `io.codehunters.parameter.jdbc`, `CachingParameterClient` in `io.codehunters.parameter.cache`.
  See [MIGRATION-3.0.md](MIGRATION-3.0.md).

## 2.0.0

### Breaking
- `ParameterModule` is a record (`ParameterModule.of(code)`, `code()`) instead of an enum. The
  constants keep their names; `valueOf`, `values()`, `name()`, `ordinal()` and `switch` over it are
  gone. Rows with a module unknown to the SDK now map instead of returning `null`. `of(code)`
  returns the constant for a known code, so `==` still works for those; an unknown code is a new
  instance each call, so compare with `equals`. `EnumMap`/`EnumSet` keyed by it no longer
  compile. Jackson now writes it as `{"code":"AUTH"}` instead of `"AUTH"`. Codes are trimmed.
- `findByModule`, `findByModules` and `findByModuleAndSubmodules` return unmodifiable lists
  (1.x returned mutable `ArrayList`s); `sort`, `add` or `removeIf` on them throws
  `UnsupportedOperationException`.
- A `null` element in the collections given to `findByModules` or `findByModuleAndSubmodules`
  throws `IllegalArgumentException`.
- A malformed UUID column throws `ParameterAccessException` naming the column, without its value
  (1.x threw `IllegalArgumentException` with the value).
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
- `CachingParameterClient`, a TTL cache used when `cache-ttl` is positive: least recently used
  eviction at 1024 answers, one shared query for concurrent misses, failures never cached.
- `JdbcParameterAdapter(JdbcClient, String tableName)`, accepting `table`, `schema.table` or
  `database.schema.table`.

### Removed
- Lombok, and the `compileOnly` dependency on `spring-boot-starter-jdbc`.
