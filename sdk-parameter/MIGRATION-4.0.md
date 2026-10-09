# Migrating sdk-parameter 3.x to 4.0

4.0.0 removes the module constants and supports PostgreSQL only. Properties and beans are
unchanged.

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
