# Changelog — sdk-totp

## 2.0.0

First release as `sdk-totp`; 1.x was published as `sdk-utils`.

### Breaking
- The artifact is `io.codehunters:sdk-totp`. It declares the `io.codehunters:sdk-utils` capability,
  so Gradle refuses a graph that holds both. The guard is Gradle-only: Maven resolves both jars and
  the same classes twice without an error, so Maven builds must exclude `sdk-utils`. The
  `io.codehunters.totp` package is unchanged.
- Properties moved from `vitxo.totp.*` to `codehunters.totp.*`. The old keys are honoured, with a
  deprecation warning, until 3.0.
- `TotpConfig` requires a time step that is a whole number of seconds, from one second to ten
  minutes (`TotpConfig.MAX_TIME_STEP`). 1.x accepted `1500ms` (silently truncated to 1 s), `500ms`
  (division by zero on first use) and any long step, which kept one code valid for the whole step.
  With Spring Boot, such a value now fails startup.
- `verify(..., variance, ...)` throws `IllegalArgumentException` for a variance outside `0..10`.
  1.x treated a negative variance as `0` and accepted any upper bound.
- Codes generated before the epoch use floor division. Every pre-epoch instant that is not a
  multiple of the step lands one window earlier than in 1.x, which truncated toward zero (with a
  30 s step, -31 s was counter -1 in 1.x and is -2 now).
- `TotpException` messages for invalid Base32 no longer include the offending character.
- Base32 decoding is strict. A length that no whole number of bytes encodes to (1, 3 or 6
  characters modulo 8), non-zero trailing bits, padding anywhere but the end, and separators other
  than spaces fail with `TotpException`. 1.x dropped the extra bits, so a secret mistyped during
  manual enrollment decoded to a different key and every code failed.
- `TotpSecret.generate(int)` rejects lengths below 16 bytes (`MIN_KEY_LENGTH`, RFC 4226 R6) with
  `TotpException`; 1.x created any length and threw `NegativeArraySizeException` for negative ones.
- `TotpSecret.generate()` creates 64-byte keys, the hash length of the default HMAC-SHA512 (1.x:
  20 bytes). Use `generate(TotpAlgorithm.HMAC_SHA1)` for 20-byte keys.
- `new TotpGenerator(null)` throws `NullPointerException` at construction instead of on first use.

### Fixed
- Codes are padded with `Locale.ROOT`. Under a default locale with non-ASCII digits (for example
  `ar-SA-u-nu-arab`), 1.x produced codes that never matched.
- Base32 decoding upper-cases with `Locale.ROOT`. Under a Turkish default locale, 1.x rejected any
  lower-case secret containing `i`.
- `verify` compares candidates in constant time and returns `false` for a `null` code instead of
  throwing `NullPointerException`.
- Legacy `vitxo.totp.*` keys set as environment variables are honoured in both forms
  (`VITXO_TOTP_TIME_STEP` and `VITXO_TOTP_TIMESTEP`). Only the documented keys (`time-step`,
  `digits`, `algorithm`) are aliased.

### Added
- `TotpSecret.fromHex(String)`, `TotpSecret.generate(TotpAlgorithm)`, `TotpSecret.MIN_KEY_LENGTH`
  and `TotpAlgorithm.keyLength()`.
- `TotpGenerator.MAX_VARIANCE` and `TotpConfig.MAX_TIME_STEP`.
- `TotpGenerator(TotpConfig, Clock)`. The auto-configuration passes the application's `Clock` bean
  when there is exactly one.
- Tests for every RFC 6238 Appendix B vector (SHA1, SHA256, SHA512).

### Changed
- One shared `SecureRandom` instead of a new instance per generated secret.
- `TotpSecret.equals` compares keys in constant time.

### Deprecated
- `TotpDigits.FOUR`: below the six-digit minimum of RFC 4226.
