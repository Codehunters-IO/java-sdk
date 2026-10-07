# Changelog — sdk-commons

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
