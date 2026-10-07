# Changelog — sdk-totp

## 2.0.0

First release as `sdk-totp`; 1.x was published as `sdk-utils`.

### Breaking
- The artifact is `io.codehunters:sdk-totp`. It declares the `io.codehunters:sdk-utils` capability,
  so Gradle refuses a graph that holds both. The `io.codehunters.totp` package is unchanged.
- Properties moved from `vitxo.totp.*` to `codehunters.totp.*`. The old keys are honoured, with a
  deprecation warning, until 3.0.
- `TotpConfig` requires a time step that is a whole number of seconds and at least one second.
  1.x accepted `1500ms` (silently truncated to 1 s) and `500ms` (division by zero on first use).
  With Spring Boot, such a value now fails startup.
- `verify(..., variance, ...)` throws `IllegalArgumentException` for a variance outside `0..10`.
  1.x treated a negative variance as `0` and accepted any upper bound.
- Codes generated before the epoch use floor division. They differ from 1.x for instants in
  `(-step, 0)`, which 1.x put in the same window as the epoch.
- `TotpException` messages for invalid Base32 no longer include the offending character.

### Fixed
- Codes are padded with `Locale.ROOT`. Under a default locale with non-ASCII digits (for example
  `ar-SA-u-nu-arab`), 1.x produced codes that never matched.
- Base32 decoding upper-cases with `Locale.ROOT`. Under a Turkish default locale, 1.x rejected any
  lower-case secret containing `i`.
- `verify` compares candidates in constant time and returns `false` for a `null` code instead of
  throwing `NullPointerException`.

### Added
- `TotpSecret.fromHex(String)`.
- `TotpGenerator.MAX_VARIANCE`.
- Tests for every RFC 6238 Appendix B vector (SHA1, SHA256, SHA512).

### Changed
- One shared `SecureRandom` instead of a new instance per generated secret.
