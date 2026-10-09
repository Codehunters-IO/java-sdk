# Migrating sdk-parameter 3.x to 4.0

4.0.0 removes the module constants, supports PostgreSQL only and moves the cache to Caffeine.
Existing properties and beans keep working; one property and one bean are added.

## Module constants are gone

`ParameterModule` no longer ships product module codes. Define the ones your application uses,
with the same codes as before so existing rows keep matching:

```java
// 3.x
parameters.lookup(ParameterModule.AUTH, "session", "TTL");

// 4.0: one constant per module, in your own code
public final class Modules {
  public static final ParameterModule AUTH = ParameterModule.of("AUTH");
  private Modules() {}
}
parameters.lookup(Modules.AUTH, "session", "TTL");
```

| Removed constant | Replacement |
|---|---|
| `ParameterModule.GENERAL` | `ParameterModule.of("GENERAL")` |
| `ParameterModule.AUTH` | `ParameterModule.of("AUTH")` |
| `ParameterModule.CONFIG_SERVER` | `ParameterModule.of("CONFIG_SERVER")` |
| `ParameterModule.FILE_SHARE` | `ParameterModule.of("FILE_SHARE")` |
| `ParameterModule.MANAGEMENT` | `ParameterModule.of("MANAGEMENT")` |
| `ParameterModule.NOTIFICATIONS` | `ParameterModule.of("NOTIFICATIONS")` |
| `ParameterModule.PAYMENT` | `ParameterModule.of("PAYMENT")` |
| `ParameterModule.RAFFLES` | `ParameterModule.of("RAFFLES")` |

`of(code)` now returns a new instance every time, also for these codes, and the row mapper does the
same. Replace any `module == SOME_CONSTANT` with `module.equals(SOME_CONSTANT)`: `==` is now always
false between a module you built and one read from the database.

## SQL Server is no longer supported

PostgreSQL is the only database the SDK is built and tested against. The reference DDL ships in
the jar at `db/migration/parameter/postgresql/parameter_create.sql` and is reproduced in the
[README](README.md#the-table). An existing table with the same column names keeps working; the
DDL adds a check on `type` and a unique index on the live `(module, submodule, name)` rows that
an older table may lack.

## The cache is Caffeine

Nothing to change when you only set `codehunters.parameter.cache-ttl`. Otherwise:

| 3.x | 4.0 |
|---|---|
| `CachingParameterClient.DEFAULT_MAX_ENTRIES` | `ParameterCache.DEFAULT_MAX_ENTRIES` |
| Fixed size of 1024 answers | `codehunters.parameter.cache-max-entries` (default `1024`) |
| A change is seen when the TTL runs out | Also at once after `ParameterCache.invalidate(module)` or `invalidateAll()` |
| Strict least-recently-used eviction | Caffeine's W-TinyLFU: a full cache may keep an older, often-read answer over a newer one |

`com.github.ben-manes.caffeine:caffeine` is now a runtime dependency, at the version Spring Boot
manages (3.2.x).

The `parameterCache` bean is always present, disabled when `cache-ttl` is `0s`, so injecting it
needs no condition. With Micrometer and a single `MeterRegistry`, the enabled cache publishes
`cache.gets`, `cache.puts`, `cache.evictions` and `cache.size` tagged
`cache=codehunters.parameter.cache`.
