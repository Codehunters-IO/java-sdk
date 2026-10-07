# Migrating sdk-utils 1.x to sdk-totp 2.0

1. **Dependency.** Replace `io.codehunters:sdk-utils` with `io.codehunters:sdk-totp:2.0.0`, or take
   the version from `io.codehunters:sdk-bom`. If another library still brings `sdk-utils`
   transitively, Gradle fails the build with a capability conflict; resolve it with
   `capabilitiesResolution` or upgrade that library. Maven has no such check: exclude
   `io.codehunters:sdk-utils` from any dependency that brings it, or the same classes end up on the
   classpath twice. Imports do not change.
2. **Properties.** Rename `vitxo.totp.time-step`, `vitxo.totp.digits` and `vitxo.totp.algorithm` to
   `codehunters.totp.*`. The old keys keep working, with a warning, until 3.0.
3. **Time step.** Use whole seconds from `1s` to `10m`. A value like `1500ms`, `500ms` or `1h` now
   stops the application from starting.
4. **Variance.** Pass a variance between `0` and `10`. Code that passed a negative value to mean
   "exact match" passes `0`.
5. **Exceptions.** `verify` with a `null` code returns `false`. Code that caught
   `NullPointerException` there can drop the catch.
6. **Codes before 1970.** Only matters if you generate codes for instants before the epoch: every
   pre-epoch instant that is not a multiple of the step now lands one window earlier than in 1.x.
7. **Stored Base32 secrets.** Decoding is strict now. Before upgrading, check that every stored
   secret decodes with `TotpSecret.fromBase32`; one that fails was stored with a wrong length or
   stray characters and was producing the wrong codes in 1.x too. Spaces between groups are still
   accepted.
8. **Generated secrets.** `TotpSecret.generate()` now creates 64 bytes (the default HMAC-SHA512).
   If secrets are typed into authenticator apps that use HMAC-SHA1, generate them with
   `TotpSecret.generate(TotpAlgorithm.HMAC_SHA1)` (20 bytes). `generate(int)` needs at least 16.
9. **Four-digit codes.** `TotpDigits.FOUR` is deprecated; move to `SIX` or `EIGHT`.
