# sdk-parameter

Typed, read-only access to the shared parameters table over JDBC. With a single `DataSource` on
the context, the SDK registers a `ParameterClient` that reads enabled, non-deleted rows.

```kotlin
implementation(platform("io.codehunters:sdk-bom:<version>"))
implementation("io.codehunters:sdk-parameter")
```

The application brings `spring-jdbc` (for example through `spring-boot-starter-jdbc`) and the
PostgreSQL JDBC driver. PostgreSQL is the only supported database.

## The table

The SDK reads the table and never creates or writes it. The reference DDL ships in the jar at
`db/migration/parameter/postgresql/parameter_create.sql`; copy it into your own migrations (the
file has no Flyway version prefix, so a `classpath:db/migration` scan ignores it). It needs
PostgreSQL 13 or later for `gen_random_uuid()`; on 12 or earlier run
`CREATE EXTENSION IF NOT EXISTS pgcrypto;` first:

```sql
CREATE TABLE parameters (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    module      VARCHAR(64)  NOT NULL,
    submodule   VARCHAR(64)  NOT NULL DEFAULT '',
    name        VARCHAR(128) NOT NULL,
    value       TEXT         NULL,
    type        VARCHAR(16)  NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    description VARCHAR(500) NULL,
    created_by  UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  UUID         NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ  NULL,
    CONSTRAINT pk_parameters PRIMARY KEY (id),
    CONSTRAINT ck_parameters_type CHECK (type IN ('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'JSON'))
);

CREATE UNIQUE INDEX ux_parameters_module_submodule_name
    ON parameters (module, submodule, name)
    WHERE deleted_at IS NULL;
```

- Rows with `enabled = false` or a `deleted_at` are never returned.
- The empty submodule is `''`, not `NULL`: a `NULL` submodule never matches.
- `type` holds a `ParameterType` name; another value maps to `null` (see Failures).
- A different table name or schema is set with `codehunters.parameter.table-name`; the column
  names are fixed.

## Usage

`ParameterClient`, `Parameter`, `ParameterModule`, `ParameterType` and the exceptions are in
`io.codehunters.parameter`. Upgrading from 3.x: see [MIGRATION-4.0.md](MIGRATION-4.0.md); from
2.x: [MIGRATION-3.0.md](MIGRATION-3.0.md).

```java
import io.codehunters.parameter.Parameter;
import io.codehunters.parameter.ParameterModule;

ParameterModule orders = ParameterModule.of("ORDERS");

Optional<Parameter> max = parameters.lookup(orders, "checkout", "MAX_ITEMS");
int limit = max.map(Parameter::asInteger).orElse(100);

Map<String, String> settings = parameters.asMap(ParameterModule.of("LOYALTY"), "rewards");
```

- `ParameterModule` is a record holding the module code. The SDK defines no codes: name yours
  with `ParameterModule.of(code)`, ideally as constants of your application. Compare modules with
  `equals`. Codes are trimmed and case-sensitive: `of("auth")` is not `of("AUTH")`.
- Jackson writes a `ParameterModule` as `{"code":"AUTH"}`, not as the 1.x string `"AUTH"`.
- A `null` element in the collections given to `findByModules` or `findByModuleAndSubmodules`
  throws `IllegalArgumentException`.
- A `null` submodule in `lookup` and a blank one anywhere mean the empty submodule. A `null`
  submodule in `findByModule`, `asMap` and `asMapByName` means every submodule.
- `asMap` and `asMapByName` keep the name order and may hold `null` values. When two submodules
  return the same name they throw `ParameterException`; pass a submodule to narrow the query.
- Returned lists and maps are unmodifiable.

### Typed values

| Accessor | Declared type | Accepts |
|---|---|---|
| `asString()` | `STRING` | any value, `null` included |
| `asLong()` / `asInteger()` | `INTEGER` | a decimal integer in range, surrounding spaces ignored |
| `asBigDecimal()` | `DECIMAL` | a decimal number, surrounding spaces ignored |
| `asBoolean()` | `BOOLEAN` | `true` or `false`, any case, surrounding spaces ignored |
| `value()` | any (`JSON` included) | the raw string |

A different declared type, a missing value, or a value the accessor cannot read throws
`ParameterTypeMismatchException`. Its message names the parameter but never includes the value,
which may be a secret.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `codehunters.parameter.enabled` | `true` | Turn the auto-configuration off |
| `codehunters.parameter.table-name` | `parameters` | Table to read: `table`, `schema.table` or `database.schema.table` |
| `codehunters.parameter.cache-ttl` | `0s` | How long answers are cached; `0s` disables the cache |
| `codehunters.parameter.cache-max-entries` | `1024` | Most answers kept while the cache is on |

An invalid table name, a negative TTL or a `cache-max-entries` below one fails at startup. The table name is an unquoted
identifier, so PostgreSQL's rules for unquoted names apply: a reserved word such as `user` fails
at query time, and the name is folded to lower case (a table created with a quoted mixed-case
name is not found).

The cache is off by default: every call reads the table, so a change is seen at once. Turn it on
with a TTL that matches how stale a parameter may be (for example `cache-ttl: 5m`).

With a positive `cache-ttl`, `CachingParameterClient` keeps each answer (absent parameters
included) for that long, per method and arguments, in a Caffeine cache (`ParameterCache`):

- A blank submodule counts as the empty submodule, and module or submodule collections are
  compared as sorted sets, so order and repeats do not create new entries.
- It holds at most `cache-max-entries` answers; when full, Caffeine evicts the answers least
  likely to be used again (not strictly the least recently used one).
- Concurrent misses for the same arguments share one database query, run on the first caller's
  thread. A failure reaches every waiting caller and is not cached.
- `asMap` and `asMapByName` are built from the cached `findByModule` answer, so the three agree.

A change in the table is seen once the TTL runs out, or at once after an invalidation:

```java
@Service
class ParameterAdmin {
  private final ParameterCache cache;

  ParameterAdmin(ParameterCache cache) {
    this.cache = cache;
  }

  void afterUpdate(ParameterModule module) {
    cache.invalidate(module); // every cached answer reading this module, multi-module queries too
  }

  void afterBulkLoad() {
    cache.invalidateAll();
  }
}
```

The `ParameterCache` bean always exists; with the cache off it is disabled and invalidating it does
nothing, so the code above needs no condition. Invalidation is local to the instance: other
replicas keep their answers until their TTL runs out.

### Metrics

With Micrometer on the classpath and a single `MeterRegistry`, the enabled cache publishes
Micrometer's standard cache meters (`cache.gets` with `result=hit|miss`, `cache.puts`,
`cache.evictions`, `cache.size`, ...) tagged `cache=codehunters.parameter.cache`. Outside Spring,
bind them with `new ParameterCacheMetrics(cache).bindTo(registry)`.

## Beans

| Bean | Notes |
|---|---|
| `parameterClient` | Replaced by any `ParameterClient` bean of the application |
| `parameterCache` | The `ParameterCache` the default client uses; disabled when `cache-ttl` is `0s`. Replaced by any `ParameterCache` bean of the application |
| `parameterJdbcClient` | Not an autowiring candidate; inject it with `@Qualifier("parameterJdbcClient")`. A bean with that name replaces it |

The auto-configuration backs off when there is no `DataSource`, or several with none primary.

## Failures

Database errors throw `ParameterAccessException` wrapping Spring's `DataAccessException`. An
unknown `type` value in a row is logged and mapped to `null`; reading such a parameter with a
typed accessor throws `ParameterTypeMismatchException`.

Migrating from 3.x: see [MIGRATION-4.0.md](MIGRATION-4.0.md). From 1.x:
[MIGRATION-2.0.md](MIGRATION-2.0.md).
