# Migrating to sdk-commons 1.1

`io.codehunters:sdk-commons` 1.1.0 contains TOTP (`io.codehunters.totp`), which shipped as
`sdk-totp` 2.0.0 and, before that, as `sdk-utils` 1.x
([ADR 0016](../docs/adr/0016-consolidate-web-and-commons-artifacts.md)). Code using the
`io.codehunters.commons.*` helpers of 1.0.0 needs no change, but a Spring Boot application using
them now also gets the TOTP auto-configuration: a `TotpGenerator` bean and the binding of
`codehunters.totp.*`, where an invalid value stops startup. Set `codehunters.totp.enabled=false` to
leave it out.

## From sdk-totp 2.0.0

Imports, properties and behaviour are those of 2.0.0; the coordinates change and the auto-configuration
gains a switch, `codehunters.totp.enabled` (default `true`).

| Before | Now |
|---|---|
| `io.codehunters:sdk-totp:2.0.0` | `io.codehunters:sdk-commons:1.1.0` |

1. **Dependency.** Replace it with `sdk-commons`, or import `sdk-bom` 1.2.0 and declare
   `io.codehunters:sdk-commons` without a version.
2. **Gradle.** `sdk-commons` declares the capabilities `io.codehunters:sdk-totp` and
   `io.codehunters:sdk-utils`. If another library still brings either, the build fails with a
   capability conflict. Prefer excluding the old module from that library or upgrading it. If you
   need `capabilitiesResolution`, select `sdk-commons` explicitly:

   ```kotlin
   configurations.all {
       resolutionStrategy.capabilitiesResolution.withCapability("io.codehunters:sdk-totp") {
           select("io.codehunters:sdk-commons:1.1.0")
       }
   }
   ```

   Do not use `selectHighestVersion()`: `sdk-totp` 2.0.0 is higher than the capability version
   1.1.0, so it would win and evict `sdk-commons`, and the `io.codehunters.commons.*` helpers would
   fail with `NoClassDefFoundError`. The same applies to `sdk-utils`.
3. **Maven.** Add an `<exclusion>` for `io.codehunters:sdk-totp` and `io.codehunters:sdk-utils` to any
   dependency that still brings them, or the same classes end up on the classpath twice.
4. **New runtime dependency.** `sdk-commons` now brings `io.codehunters:sdk-support` (internal; it
   holds the property aliases). Spring Boot stays `compileOnly`.

## From sdk-utils 1.x

Replace `io.codehunters:sdk-utils` with `io.codehunters:sdk-commons:1.1.0` (steps 1 to 4 above apply
the same way), then:

1. **Properties.** Rename `vitxo.totp.time-step`, `vitxo.totp.digits` and `vitxo.totp.algorithm` to
   `codehunters.totp.*`. The old keys keep working, with a warning, until sdk-commons 2.0.0.
2. **Time step.** Use whole seconds from `1s` to `10m`. A value like `1500ms`, `500ms` or `1h` now
   stops the application from starting.
3. **Variance.** Pass a variance between `0` and `10`. Code that passed a negative value to mean
   "exact match" passes `0`.
4. **Exceptions.** `verify` with a `null` code returns `false`. Code that caught
   `NullPointerException` there can drop the catch.
5. **Codes before 1970.** Only matters if you generate codes for instants before the epoch: every
   pre-epoch instant that is not a multiple of the step now lands one window earlier than in 1.x.
6. **Stored Base32 secrets.** Decoding is strict now. Before upgrading, check that every stored
   secret decodes with `TotpSecret.fromBase32`; one that fails was stored with a wrong length or
   stray characters and was producing the wrong codes in 1.x too. Spaces between groups are still
   accepted.
7. **Generated secrets.** `TotpSecret.generate()` now creates 64 bytes (the default HMAC-SHA512).
   If secrets are typed into authenticator apps that use HMAC-SHA1, generate them with
   `TotpSecret.generate(TotpAlgorithm.HMAC_SHA1)` (20 bytes). `generate(int)` needs at least 16.
8. **Four-digit codes.** `TotpDigits.FOUR` is deprecated; move to `SIX` or `EIGHT`.
