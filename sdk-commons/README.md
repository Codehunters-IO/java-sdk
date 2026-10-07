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
