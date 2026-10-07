# Migrating sdk-utils 1.x to sdk-totp 2.0

1. **Dependency.** Replace `io.codehunters:sdk-utils` with `io.codehunters:sdk-totp:2.0.0`, or take
   the version from `io.codehunters:sdk-bom`. If another library still brings `sdk-utils`
   transitively, Gradle fails the build with a capability conflict; resolve it with
   `capabilitiesResolution` or upgrade that library. Imports do not change.
2. **Properties.** Rename `vitxo.totp.time-step`, `vitxo.totp.digits` and `vitxo.totp.algorithm` to
   `codehunters.totp.*`. The old keys keep working, with a warning, until 3.0.
3. **Time step.** Use whole seconds of at least `1s`. A value like `1500ms` or `500ms` now stops the
   application from starting.
4. **Variance.** Pass a variance between `0` and `10`. Code that passed a negative value to mean
   "exact match" passes `0`.
5. **Exceptions.** `verify` with a `null` code returns `false`. Code that caught
   `NullPointerException` there can drop the catch.
6. **Codes before 1970.** Only matters if you generate codes for instants before the epoch; those
   now follow RFC 6238 floor division and differ from 1.x in the first window before the epoch.
