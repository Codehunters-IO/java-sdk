# sdk-commons

Small, framework-free helpers that the JDK does not provide, and RFC 6238 TOTP. Artifact
`io.codehunters:sdk-commons`, packages `io.codehunters.commons.*` and `io.codehunters.commons.totp`.

The helpers in `io.codehunters.commons.*` depend on the JDK only and have no auto-configuration: call
the classes directly. The [TOTP](#totp--iocodehunterscommonstotp) core is JDK-only too; its optional
Spring Boot auto-configuration (`io.codehunters.commons.totp.config`) activates only in a Boot
application that sets `codehunters.totp.enabled=true`.
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
| `error` | `ClientInputException` | Input a client got wrong, with a stable code; answered with 400 by `sdk-web` |
| `io.codehunters.commons.totp` | `TotpGenerator`, `TotpSecret`, `TotpConfig` | RFC 6238 codes: generate, verify once, key URI ([below](#totp--iocodehunterscommonstotp)) |

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
- **`ClientInputException`** says "the client sent something this code cannot accept". It is an
  `IllegalArgumentException`, so existing `catch` blocks keep working, with a stable `code()`
  matching `[A-Z][A-Z0-9_]*` (anything else, and a blank message, is rejected). The message may be
  sent to the client as the problem detail, so it must state the rule that was broken and never
  repeat the input or other secret, personal or internal data: "sort property is not allowed;
  allowed: created, name", not "cannot sort by passwordHash". `sdk-web` answers it with 400 and the
  code, also when it is the cause of another exception, and localizes the detail with the bundle
  key `error.<CODE>`. Programming errors (a `null` argument, a misconfigured whitelist) stay plain
  `IllegalArgumentException`s. Codes thrown by the SDK: `INVALID_SORT`, `INVALID_SEARCH`
  (`sdk-jpa`), `BARCODE_IMAGE_REJECTED`, `BARCODE_CONTENT_REJECTED` (`sdk-barcode`).

  ```java
  throw new ClientInputException("INVALID_DATE_RANGE", "the range may span at most 31 days");
  ```

## TOTP — `io.codehunters.commons.totp`

RFC 6238 time-based one-time passwords: generation, verification and shared-secret handling.
Formerly the `sdk-totp` artifact (and `sdk-utils` before it); the `codehunters.totp.*` properties
did not change ([ADR 0016](../docs/adr/0016-consolidate-web-and-commons-artifacts.md)). Coming from
either: [MIGRATION-1.1.md](MIGRATION-1.1.md). In 2.0.0 the package moved from `io.codehunters.totp`
to `io.codehunters.commons.totp`: [MIGRATION-2.0.md](MIGRATION-2.0.md). In 3.0.0 the defaults
became authenticator-app compatible and the auto-configuration opt-in:
[MIGRATION-3.0.md](MIGRATION-3.0.md).

### Quick start

```java
TotpGenerator totp = new TotpGenerator(TotpConfig.defaultConfig()); // HMAC-SHA1, 6 digits, 30 s

TotpSecret secret = TotpSecret.generate();                        // 20 random bytes
String uri = totp.provisioningUri(secret, "ACME Co", "john.doe@example.com");
// otpauth://totp/ACME%20Co:john.doe%40example.com?secret=...&issuer=ACME%20Co
//   &algorithm=SHA1&digits=6&period=30  -> render as a QR code for the authenticator app

String code = totp.generate(secret);
boolean ok = totp.verify(secret, submitted, 1);                  // see "One-time use" below
```

`TotpConfig.defaultConfig()` is what Google Authenticator, Microsoft Authenticator and most other
apps support: HMAC-SHA1, six digits, a 30-second step (RFC 6238). Other settings
(`new TotpConfig(step, digits, algorithm)`) only work with apps that read them from the key URI,
and many ignore them.

`TotpGenerator` works without Spring. With Spring Boot, set `codehunters.totp.enabled=true` and the
auto-configuration registers one bean built from the properties below; declare your own
`TotpGenerator` bean to replace it. The overloads without an `Instant` read the time from a
`Clock`: the application's `Clock` bean when there is exactly one, otherwise the UTC system clock.
Outside Spring, pass one to `new TotpGenerator(config, clock)`.

### Enrolment: the key URI

`provisioningUri(secret, issuer, account)` builds the `otpauth://totp/` URI that apps import,
usually from a QR code. It carries the Base32 secret without padding, the issuer (in the label and
as a parameter) and the generator's algorithm, digits and period. Every component is
percent-encoded as UTF-8 per RFC 3986: only `A-Z a-z 0-9 - . _ ~` stay as they are, so a space is
`%20` and `@` is `%40`. Issuer and account must not be blank and must not contain `:`, control
characters or invisible format characters (bidi overrides, zero-width spaces), which could make the
app show a different label; otherwise it throws `IllegalArgumentException` with a message that does
not repeat the value. The URI contains the secret: never log it.

Secrets come from raw bytes (`fromBytes`), Base32 (`fromBase32`) or hex (`fromHex`, as in the RFC
test vectors). `toString()` never shows the key, and an invalid Base32 or hex input fails with a
`TotpException` that does not repeat the input.

- **Generated keys** are at least 16 bytes (`TotpSecret.MIN_KEY_LENGTH`, RFC 4226).
  `generate(TotpAlgorithm)` sizes the key to the hash: 20, 32 or 64 bytes. `generate()` uses the
  default HMAC-SHA1 and creates 20 bytes.
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
| `codehunters.totp.enabled` | `false` | `true` registers the `TotpGenerator` bean. Off by default, so applications that only use the helpers get no TOTP bean. |
| `codehunters.totp.time-step` | `30s` | Whole seconds, from `1s` to `10m`. Anything else fails startup. |
| `codehunters.totp.digits` | `six` | `six` or `eight`; `four` is deprecated (below RFC 4226). |
| `codehunters.totp.algorithm` | `hmac-sha1` | `hmac-sha1`, `hmac-sha256` or `hmac-sha512`. Keep the algorithm secrets were enrolled with. |

The 1.x keys under `vitxo.totp.*` are still read, with a deprecation warning, until sdk-commons
4.0.0. A key set under `codehunters.totp.*` wins over the old one.

Before 3.0.0 the default algorithm was HMAC-SHA512 and the auto-configuration was on by default:
[MIGRATION-3.0.md](MIGRATION-3.0.md).

### Verification

- `variance` is the number of time steps accepted on each side of the current one, from `0` to
  `10` (`TotpGenerator.MAX_VARIANCE`). Other values throw `IllegalArgumentException`.
- Every candidate in the window is compared in constant time, and the comparison does not stop at
  the first match. A `null` code returns `false`.
- `verify` does not stop a code from being used twice inside its window. Use `verifyAfter` for
  one-time codes (below). `verifyStep` returns the matched time step without that check; when a
  code matches several steps of the window, the latest one is returned.
- **Rate-limit attempts.** The library has no throttling. Each guess is accepted with probability
  `(2 × variance + 1) / 10^digits`: one in about 476 with four digits and variance 10, one in about
  47,600 with six digits. Limit failed attempts per secret, as RFC 4226 §7.3 asks.
- Codes are always ASCII digits, whatever the JVM's default locale. Instants before the epoch use
  floor division, so they land in the correct window.

### One-time use: reject replayed codes

A code stays valid for its whole step plus the drift window, so an observed code can be replayed.
Store the last accepted step per user and pass it to `verifyAfter`, which accepts only a later
step and returns the one it matched:

```java
OptionalLong step = totp.verifyAfter(user.totpSecret(), submitted, 1, user.lastTotpStep());
if (step.isEmpty()) {
  throw new InvalidCodeException();             // wrong, expired or already used
}
users.saveLastTotpStep(user.id(), step.getAsLong());
```

For a user who has never logged in with a code, pass `TotpGenerator.NO_ACCEPTED_STEP`. Save the
step atomically with the check, or two requests carrying the same code can both pass; treat zero
updated rows as a replay. A nullable column holds `NULL` until the first code, so the condition must
accept it:

```sql
UPDATE users SET last_totp_step = :step
 WHERE id = :id AND (last_totp_step IS NULL OR last_totp_step < :step)
```

`verifyAfter(secret, code, variance, lastAcceptedStep, at)` does the same against a given instant
instead of the clock.

The implementation is checked against every test vector in RFC 6238, Appendix B.
