# Changelog — sdk-commons

## 3.0.0

### Breaking
- TOTP defaults follow RFC 6238 and what authenticator apps support: `TotpConfig.defaultConfig()`
  and an unset `codehunters.totp.algorithm` are HMAC-SHA1 (was HMAC-SHA512), and
  `TotpSecret.generate()` creates 20 bytes (was 64). Codes from the 2.x defaults did not match
  Google or Microsoft Authenticator. Secrets enrolled with SHA-512 must keep it:
  `codehunters.totp.algorithm=hmac-sha512`. See [MIGRATION-3.0.md](MIGRATION-3.0.md).
- The TOTP auto-configuration is opt-in: `codehunters.totp.enabled` defaults to `false` (was
  `true`), so applications that only use the helpers get no TOTP bean.

### Added
- `io.codehunters.commons.error.ClientInputException`: an `IllegalArgumentException` with a stable
  `code()` (upper snake case, `[A-Z][A-Z0-9_]*`) for input a client got wrong. Its message is safe
  for clients by contract: it never repeats the input. `sdk-web` 3.0.0 answers it with 400 and the
  code; `sdk-jpa` and `sdk-barcode` 2.1.0 throw subclasses of it.
- `TotpGenerator.verifyAfter(secret, code, variance, lastAcceptedStep[, at])` and `verifyStep`: return
  the matched time step, so a caller can store it and reject replayed codes. Comparison stays
  constant-time. `TotpGenerator.NO_ACCEPTED_STEP` for a secret with no accepted code yet.
- `TotpGenerator.provisioningUri(secret, issuer, account)`: the `otpauth://totp/` key URI, with
  RFC 3986 percent-encoding; issuer and account are validated and must not contain `:`, control
  characters or invisible format characters (bidi overrides, zero-width spaces).

### Removed
- The pre-2.0 TOTP property aliases, their `EnvironmentPostProcessor` (`TotpLegacyProperties`) and
  their deprecated configuration metadata: only `codehunters.totp.*` binds.
- The runtime dependency on `sdk-support`: `sdk-commons` depends on the JDK only.

## 2.0.0

### Breaking
- TOTP moves from `io.codehunters.totp` to `io.codehunters.commons.totp` (and `.config`), so every
  package in the artifact starts with `io.codehunters.commons`. Only imports change; class names,
  properties and behaviour do not. See [MIGRATION-2.0.md](MIGRATION-2.0.md).

## 1.1.0

Absorbs `sdk-totp` ([ADR 0016](../docs/adr/0016-consolidate-web-and-commons-artifacts.md)).

- `io.codehunters.totp` (RFC 6238 TOTP) ships in this artifact, unchanged from `sdk-totp` 2.0.0,
  with its `codehunters.totp.*` auto-configuration.
- Declares the Gradle capabilities `io.codehunters:sdk-totp` and `io.codehunters:sdk-utils`: Gradle
  refuses a graph that also holds either. Maven builds must exclude them. See
  [MIGRATION-1.1.md](MIGRATION-1.1.md).
- New switch `codehunters.totp.enabled` (default `true`): a Boot application that only uses the
  helpers can leave the TOTP auto-configuration out.
- New runtime dependency: `sdk-support`. Spring Boot stays
  `compileOnly`; the helpers in `io.codehunters.commons` still depend on the JDK only, and so does
  the TOTP core (ArchUnit).

## 1.0.0

First release ([ADR 0010](../docs/adr/0010-sdk-commons-for-framework-free-helpers.md)).

- `Masking`: keep the first/last code points of a value, mask e-mail addresses and card numbers.
  Fully masks a value when the kept part would cover it. Composes text (NFC) before counting.
- `TextNormalizer.simplify`: stable lower-case tokens for slugs, keys and file names. Strips marks
  from Latin letters only; other scripts keep theirs.
- `PasswordPolicy` / `PasswordCheck`: length and composition rules that report every violation,
  including no-break spaces and invisible characters (`CONTAINS_INVISIBLE`).
- `PasswordGenerator`: `SecureRandom` passwords (12 to 128 characters) that always meet the default
  policy, and numeric codes.
- `RelativeTime`: coarse "n units ago / in n units" between two instants.
- `CronExpressions.quartzAt`: one-shot Quartz cron expression for years 1970 to 2099.
- `FileNames`: plain extension as `Optional`, replace the base name with a safe one.
- `Predicates.distinctByKey`: null-key-safe, thread-safe distinct-by-key filter.
- Pure JDK: no Lombok, no Spring, no Jackson; enforced by an ArchUnit test.

Coming from `codehuntersio-java-commons-support`: see
[MIGRATION-from-commons-support.md](MIGRATION-from-commons-support.md).

## Before 1.1.0: `sdk-totp`

### sdk-totp 2.0.0

First release as `sdk-totp`; 1.x was published as `sdk-utils`.

#### Breaking
- The artifact is `io.codehunters:sdk-totp`. It declares the `io.codehunters:sdk-utils` capability,
  so Gradle refuses a graph that holds both. The guard is Gradle-only: Maven resolves both jars and
  the same classes twice without an error, so Maven builds must exclude `sdk-utils`. The
  `io.codehunters.totp` package is unchanged.
- Properties moved under `codehunters.totp.*`.
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

#### Fixed
- Codes are padded with `Locale.ROOT`. Under a default locale with non-ASCII digits (for example
  `ar-SA-u-nu-arab`), 1.x produced codes that never matched.
- Base32 decoding upper-cases with `Locale.ROOT`. Under a Turkish default locale, 1.x rejected any
  lower-case secret containing `i`.
- `verify` compares candidates in constant time and returns `false` for a `null` code instead of
  throwing `NullPointerException`.

#### Added
- `TotpSecret.fromHex(String)`, `TotpSecret.generate(TotpAlgorithm)`, `TotpSecret.MIN_KEY_LENGTH`
  and `TotpAlgorithm.keyLength()`.
- `TotpGenerator.MAX_VARIANCE` and `TotpConfig.MAX_TIME_STEP`.
- `TotpGenerator(TotpConfig, Clock)`. The auto-configuration passes the application's `Clock` bean
  when there is exactly one.
- Tests for every RFC 6238 Appendix B vector (SHA1, SHA256, SHA512).

#### Changed
- One shared `SecureRandom` instead of a new instance per generated secret.
- `TotpSecret.equals` compares keys in constant time.

#### Deprecated
- `TotpDigits.FOUR`: below the six-digit minimum of RFC 4226.
