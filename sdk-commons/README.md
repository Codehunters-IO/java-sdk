# sdk-commons

Small, framework-free helpers that the JDK does not provide. Artifact `io.codehunters:sdk-commons`,
packages `io.codehunters.commons.*`. No runtime dependencies besides the JDK (an ArchUnit test
enforces it), no Spring auto-configuration: call the classes directly.

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
  everything is masked (`keep("abcd", 2, 2)` is `"****"`). Lengths are counted in code points. A
  malformed e-mail address is masked completely.
- **No value in exceptions.** Invalid input throws `IllegalArgumentException` (including for
  `null`) with a message that never repeats the input, so it is safe to log.
- **`TextNormalizer.simplify`** lower-cases with `Locale.ROOT`, removes Latin diacritics, spells
  out `ß æ ø œ ł ð đ þ`, drops dots and quotes, and turns everything else that is not a letter,
  digit or combining mark into one `_`, trimmed at both ends. Letters of any script are kept, and
  marks outside the Latin diacritics block (Japanese dakuten, for instance) are preserved. The
  result can be empty.
- **Passwords** are generated with a shared `SecureRandom`, between 12 and 128 characters, and
  never use `i l o 0 1 I L O`. Each one contains a lower-case letter, an upper-case letter, a digit
  and a symbol by construction; there is no retry loop. `PasswordCheck` holds only the broken
  rules, never the password. Whitespace is always a violation.
- **`RelativeTime`** floors its amount and uses 30-day months and 365-day years. Only an English
  rendering is built in; for other languages use `amount()`, `unit()` and `direction()` with your
  message bundles.
- **`CronExpressions.quartzAt`** is for Quartz. Spring's `CronExpression` has no year field, so it
  cannot fire only once. The scheduler evaluates the expression in its own time zone.
- **`Predicates.distinctByKey`** is stateful: create one per stream. In a parallel stream "first"
  means first to be tested.
