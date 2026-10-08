# sdk-commons

Small, framework-free helpers that the JDK does not provide, and RFC 6238 TOTP. Artifact
`io.codehunters:sdk-commons`, packages `io.codehunters.commons.*` and `io.codehunters.totp`.

The helpers in `io.codehunters.commons.*` depend on the JDK only and have no auto-configuration: call
the classes directly. The [TOTP](#totp--iocodehunterstotp) core is JDK-only too; its optional
Spring Boot auto-configuration (`io.codehunters.totp.config`) activates only in a Boot application.
The one runtime dependency is `sdk-support`, which carries the old `vitxo.totp.*` property aliases.
ArchUnit tests enforce these boundaries.

Rewritten from the loose utilities of the old `codehuntersio-java-commons-support` repository;
[MIGRATION-from-commons-support.md](MIGRATION-from-commons-support.md) maps every old function to
its replacement. Why the module is not called `sdk-utils`:
[ADR 0010](../docs/adr/0010-sdk-commons-for-framework-free-helpers.md).

## Contents

| Package | Class | What it does |
|---|---|---|
| `masking` | `Masking` | `keep(text, first, last)`, `keepFirst`, `keepLast`, `email`, `pan` |
| `text` | `TextNormalizer` | `simplify(text)`: lower-case token of letters, digits and `_` |
| `password` | `PasswordPolicy`, `PasswordCheck`, `CharacterClass`, `PasswordViolation` | Length and composition rules, reporting every broken rule |
| `password` | `PasswordGenerator` | Random passwords that always meet `PasswordPolicy.DEFAULT`, and numeric codes |
| `time` | `RelativeTime` | "3 days ago" labels from two `Instant`s, in English or as amount + unit for i18n |
| `time` | `CronExpressions` | `quartzAt(LocalDateTime)`: a Quartz expression that fires once |
| `file` | `FileNames` | `extension(name)` as `Optional`, `withBaseName(name, base)` |
| `stream` | `Predicates` | `distinctByKey(keyExtractor)` |
| `io.codehunters.totp` | `TotpGenerator`, `TotpSecret`, `TotpConfig` | RFC 6238 codes: generate and verify ([below](#totp--iocodehunterstotp)) |

## Usage

```java
Masking.keep("1234567890", 2, 2);      // "12******90"
Masking.email("john.doe@example.com"); // "j*******@example.com"
Masking.pan("4111 1111 1111 1111");    // "************1111"

TextNormalizer.simplify("Crème Brûlée 2.0"); // "creme_brulee_20"

String password = new PasswordGenerator().generate();       // 16 chars
PasswordCheck check = PasswordPolicy.DEFAULT.check(input);  // check.violations()

RelativeTime.between(createdAt, Instant.now()).toEnglish(); // "2 hours ago"
FileNames.withBaseName("photo.JPG", "avatar");             // "avatar.JPG"
```

## Behaviour worth knowing

- **Masking never reveals the whole value.** When the characters to keep would cover all of it,
  everything is masked (`keep("abcd", 2, 2)` is `"****"`). Text is composed (NFC) first and
  lengths are counted in code points, so an accent stays with its letter. A malformed e-mail
  address is masked completely. `pan` accepts digit groups separated by single spaces or dashes.
  The mask character cannot be a surrogate. Counting is by code point, not by grapheme: an emoji
  sequence joined with ZWJ counts as several characters.
- **No value in exceptions.** Invalid input throws `IllegalArgumentException` (including for
  `null`) with a message that never repeats the input, so it is safe to log.
- **`TextNormalizer.simplify`** lower-cases with `Locale.ROOT`, removes combining marks from Latin
  letters only, spells out `ß æ ø œ ł ð đ þ`, folds Greek `ς` into `σ`, drops dots and quotes, and
  turns everything else that is not a letter, digit or combining mark into one `_`, trimmed at
  both ends. Letters of any script are kept with their marks (Cyrillic `й`, Greek `ά`, Japanese
  dakuten), so distinct letters never merge. The result can be empty.
- **Passwords** are generated with a shared `SecureRandom`, between 12 and 128 characters, and
  never use `i l o 0 1 I L O`. Each one contains a lower-case letter, an upper-case letter, a digit
  and a symbol by construction; there is no retry loop. `PasswordCheck` holds only the broken
  rules, never the password. Whitespace (including no-break spaces) and invisible characters
  (control, format such as zero-width space, unpaired surrogates, private-use, unassigned) are
  always violations, and only visible punctuation or symbols count as special. Input longer than
  twice the maximum is reported as `TOO_LONG` alone, without scanning it.
- **`RelativeTime`** floors its amount and uses 30-day months (at most 11, so 360 to 364 days is
  "11 months") and 365-day years. Only an English
  rendering is built in; for other languages use `amount()`, `unit()` and `direction()` with your
  message bundles.
- **`CronExpressions.quartzAt`** is for Quartz. Spring's `CronExpression` has no year field, so it
  cannot fire only once. The scheduler evaluates the expression in its own time zone. Quartz
  only accepts years 1970 to 2099; other years throw.
- **`FileNames`** treats names as untrusted. `extension` returns only 1 to 16 letters, digits, `-`
  or `_` (a NUL, trailing space or `:stream` yields empty); it is a shape check, so still compare
  it with the types you accept. `withBaseName` rejects `.`, `..`, separators, colons, control and
  format characters, and a trailing dot or space.
- **`Predicates.distinctByKey`** is stateful: create one per stream. In a parallel stream "first"
  means first to be tested.

## TOTP — `io.codehunters.totp`

RFC 6238 time-based one-time passwords: generation, verification and shared-secret handling.
Formerly the `sdk-totp` artifact (and `sdk-utils` before it); the package and the
`codehunters.totp.*` properties did not change
([ADR 0016](../docs/adr/0016-consolidate-web-and-commons-artifacts.md)). Coming from either:
[MIGRATION-1.1.md](MIGRATION-1.1.md).

### Usage

```java
TotpSecret secret = TotpSecret.generate();          // 64 random bytes (HMAC-SHA512)
String shared = secret.toBase32();                  // give this to the authenticator app

String code = totpGenerator.generate(secret);
boolean ok = totpGenerator.verify(secret, submitted, 1);
```

`TotpGenerator` works without Spring. With Spring Boot, the auto-configuration registers one bean
built from the properties below; declare your own `TotpGenerator` bean to replace it. The
overloads without an `Instant` read the time from a `Clock`: the application's `Clock` bean when
there is exactly one, otherwise the UTC system clock. Outside Spring, pass one to
`new TotpGenerator(config, clock)`.

Secrets come from raw bytes (`fromBytes`), Base32 (`fromBase32`) or hex (`fromHex`, as in the RFC
test vectors). `toString()` never shows the key, and an invalid Base32 or hex input fails with a
`TotpException` that does not repeat the input.

- **Generated keys** are at least 16 bytes (`TotpSecret.MIN_KEY_LENGTH`, RFC 4226).
  `generate(TotpAlgorithm)` sizes the key to the hash: 20, 32 or 64 bytes. `generate()` uses the
  default HMAC-SHA512 and creates 64 bytes.
- **Imported keys** only have to be non-empty, so secrets enrolled earlier with a shorter key keep
  working.
- **Base32** is case-insensitive and accepts spaces between groups, as authenticator apps show
  them. Padding is optional, but only at the end. A length that no whole number of bytes encodes
  to, non-zero trailing bits and any other separator are rejected, so a mistyped secret fails at
  import instead of producing wrong codes.
- **Equality** compares keys in constant time. `hashCode()` is derived from the key; never log it.

### Configuration

| Property | Default | Notes |
|---|---|---|
| `codehunters.totp.time-step` | `30s` | Whole seconds, from `1s` to `10m`. Anything else fails startup. |
| `codehunters.totp.digits` | `six` | `six` or `eight`; `four` is deprecated (below RFC 4226). |
| `codehunters.totp.algorithm` | `hmac-sha512` | `hmac-sha1`, `hmac-sha256` or `hmac-sha512`. |

The 1.x keys under `vitxo.totp.*` are still read, with a deprecation warning, until sdk-commons 2.0.0. A key set
under `codehunters.totp.*` wins over the old one.

Most authenticator apps only support HMAC-SHA1 with six digits and a 30-second step. Set
`codehunters.totp.algorithm=hmac-sha1` when codes are entered from such an app.

### Verification

- `variance` is the number of time steps accepted on each side of the current one, from `0` to
  `10` (`TotpGenerator.MAX_VARIANCE`). Other values throw `IllegalArgumentException`.
- Every candidate in the window is compared in constant time, and the comparison does not stop at
  the first match. A `null` code returns `false`.
- Verification does not stop a code from being used twice inside its window. If a code must be
  single-use, store the last accepted time step per secret and reject codes at or before it.
- **Rate-limit attempts.** The library has no throttling. Each guess is accepted with probability
  `(2 × variance + 1) / 10^digits`: one in about 476 with four digits and variance 10, one in about
  47,600 with six digits. Limit failed attempts per secret, as RFC 4226 §7.3 asks.
- Codes are always ASCII digits, whatever the JVM's default locale. Instants before the epoch use
  floor division, so they land in the correct window.

The implementation is checked against every test vector in RFC 6238, Appendix B.
