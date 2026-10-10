# Changelog — sdk-parameter

## 4.0.0

PostgreSQL only. See [MIGRATION-4.0.md](MIGRATION-4.0.md).

### Breaking
- `ParameterModule` defines no constants: `GENERAL`, `AUTH`, `CONFIG_SERVER`, `FILE_SHARE`,
  `MANAGEMENT`, `NOTIFICATIONS`, `PAYMENT` and `RAFFLES` are gone. `ParameterModule.of(code)`
  takes any code and always returns a new instance, so compare modules with `equals`.
- `CachingParameterClient.DEFAULT_MAX_ENTRIES` moved to `ParameterCache.DEFAULT_MAX_ENTRIES` (still
  1024), and the cache is Caffeine instead of a hand-written map; `caffeine` is a new runtime
  dependency.

### Behaviour changes
- A full cache evicts by Caffeine's frequency-and-recency policy (W-TinyLFU) instead of strictly
  the least recently used answer. Time to live, one shared query per concurrent miss, failures
  reaching every waiting caller and never being cached are unchanged; the shared query still runs
  on the first caller's thread.
- A load that reads its own answer from the same cache (a delegate that calls back into the
  caching client with the same arguments) fails with `IllegalStateException("re-entrant load of
  …")` instead of deadlocking on its own unfinished load. Reading another answer is still allowed.
- A `null` answer from the delegate is cached as absence like any other answer, instead of being
  dropped by Caffeine and counted as a failed load.

### Added
- `ParameterCache`, the cache behind `CachingParameterClient`, registered as the `parameterCache`
  bean (disabled when `cache-ttl` is `0s`). `invalidate(module)` drops every answer reading a
  module, multi-module queries included; `invalidateAll()` drops everything.
- `CachingParameterClient(ParameterClient, ParameterCache)`, to share a cache the caller keeps.
- `codehunters.parameter.cache-max-entries` (default `1024`, at least `1`).
- Cache metrics: with Micrometer and a single `MeterRegistry`, Micrometer's standard cache meters
  tagged `cache=codehunters.parameter.cache` (`ParameterCacheMetrics`, also usable outside Spring).
- Reference PostgreSQL DDL for the parameters table, in the jar at
  `db/migration/parameter/postgresql/parameter_create.sql` and in the README.

### Removed
- SQL Server support: its integration test, the Testcontainers SQL Server and driver test
  dependencies, and the SQL Server notes in `ParameterRowMapper`. PostgreSQL is the only supported
  database.

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
