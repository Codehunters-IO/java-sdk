# sdk-parameter

Typed, read-only access to the shared parameters table over JDBC. With a single `DataSource` on
the context, the SDK registers a `ParameterClient` that reads enabled, non-deleted rows.

```kotlin
implementation(platform("io.codehunters:sdk-bom:<version>"))
implementation("io.codehunters:sdk-parameter")
```

The application brings `spring-jdbc` (for example through `spring-boot-starter-jdbc`) and its
JDBC driver.

## Usage

```java
Optional<Parameter> max = parameters.lookup(ParameterModule.RAFFLES, "tickets", "MAX");
int limit = max.map(Parameter::asInteger).orElse(100);

Map<String, String> settings = parameters.asMap(ParameterModule.of("LOYALTY"), "rewards");
```

- `ParameterModule` is a record. The constants (`GENERAL`, `AUTH`, `CONFIG_SERVER`, `FILE_SHARE`,
  `MANAGEMENT`, `NOTIFICATIONS`, `PAYMENT`, `RAFFLES`) cover the modules known at release;
  `ParameterModule.of(code)` reads any other.
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
| `codehunters.parameter.table-name` | `parameters` | Table to read, optionally `schema.table` |
| `codehunters.parameter.cache-ttl` | `0s` | How long answers are cached; `0s` disables the cache |

An invalid table name or a negative TTL fails at startup.

With a positive `cache-ttl`, `CachingParameterClient` keeps each answer (absent parameters
included) for that long, per method and arguments. Failures are not cached. It holds at most
1024 answers; when full it drops expired ones, and if none has expired it answers without caching.
A change in the table is seen once the TTL runs out.

## Beans

| Bean | Notes |
|---|---|
| `parameterClient` | Replaced by any `ParameterClient` bean of the application |
| `parameterJdbcClient` | Not an autowiring candidate; inject it with `@Qualifier("parameterJdbcClient")`. A bean with that name replaces it |

The auto-configuration backs off when there is no `DataSource`, or several with none primary.

## Failures

Database errors throw `ParameterAccessException` wrapping Spring's `DataAccessException`. An
unknown `type` value in a row is logged and mapped to `null`; reading such a parameter with a
typed accessor throws `ParameterTypeMismatchException`.

Migrating from 1.x: see [MIGRATION-2.0.md](MIGRATION-2.0.md).
