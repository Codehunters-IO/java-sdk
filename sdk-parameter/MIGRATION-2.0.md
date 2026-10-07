# Migrating sdk-parameter 1.x to 2.0

1. **Modules.** `ParameterModule.RAFFLES` and the other constants still compile. Replace
   `ParameterModule.valueOf(s)` with `ParameterModule.of(s)`, `module.name()` with
   `module.code()`, and `switch` statements over the module with `if`/`equals` or a map.
   `toString()` still returns the code.
2. **Booleans.** `asBoolean()` rejects anything but `true`/`false`. Fix rows holding `1`, `yes` or
   an empty string before upgrading, or read them with `value()`.
3. **Exceptions.** Out-of-range integers and `null` values throw `ParameterTypeMismatchException`.
   Code that caught `ArithmeticException` or `NullPointerException` catches it instead. Code that
   built `ParameterTypeMismatchException` with a value passes the parameter name.
4. **Maps.** `asMap(module, null)` and `asMapByName(module, null)` fail when a name exists in two
   submodules. Pass the submodule, or use `findByModule`.
5. **Injection.** `parameterJdbcClient` needs `@Qualifier("parameterJdbcClient")` to be injected.
6. **Several DataSources.** With more than one and none primary, the SDK backs off (1.x failed to
   start). Mark one `@Primary`, or declare your own `ParameterClient`.
7. **Opting out.** `codehunters.parameter.enabled=false` replaces excluding the auto-configuration.

There were no 1.x properties, so no key needs renaming.
