# Migrating from codehuntersio-java-commons-support

`sdk-commons` replaces the loose utilities of `io.codehunters.commons:commons-util` (5.1.0). They
were rewritten, not copied: names, signatures and some behaviour changed. The other modules of
that repository (DTOs, JPA, web, JWT, properties, mail, Jasper, barcode, IP/device lookup, batch)
are not part of the SDK.

## Kept, with a new API

| Old | New | What changed |
|---|---|---|
| `MasqueradeUtil.mask(text, position, start, end[, wildcard])` | `Masking.keep(text, first, last[, mask])` | Counts of visible characters instead of indices and an `int` position. Never returns `null`; fully masks when the kept part covers the value; composes the text (NFC) and counts code points; invalid input throws `IllegalArgumentException` without the value. |
| `MasqueradeUtil.leftMask(text, start)` | `Masking.keepLast(text, length - start)` | `leftMask` hid the first `start` characters; `keepLast` takes how many trailing characters stay visible. One difference: `leftMask(text, 0)` showed the whole value, while `keepLast(text, length)` masks all of it. |
| `MasqueradeUtil.rightMask(text, end)` | `Masking.keepFirst(text, end)` | Same visible prefix, except that keeping the whole value now masks all of it. |
| — | `Masking.email`, `Masking.pan` | New. |
| `StringSimplifier.simplifiedString(text)` | `TextNormalizer.simplify(text)` | Lower-cases with `Locale.ROOT` (the old one used the default locale). Every non-letter, non-digit character becomes `_` (the old one passed unknown symbols through), and `_` is trimmed at both ends (the old one only trimmed the end). Modifier letters such as the Japanese prolonged sound mark `ー` are kept (the old one removed them), and text is recomposed (NFC) after removing diacritics. Combining marks are removed from Latin letters only, so Cyrillic `й` and Greek `ά` keep theirs (the old one removed them), and Greek `ς` folds into `σ`. `©` becomes `_` (old: `c`). Returns `""` instead of the original text when nothing survives, and throws instead of returning `null`. |
| `PasswordUtil.randomPass([length])` | `new PasswordGenerator().generate([length])` | Length between 12 and 128, default 16 (old: 10; the old generator checked every candidate against a maximum of 10, so any length below 8 or above 10 looped forever). Uses a shared `SecureRandom` instead of `getInstanceStrong()`, which can block. No checked exception. |
| `PasswordUtil.randomNumericPass([length])` | `new PasswordGenerator().numeric(length)` | Length between 4 and 128. |
| `PasswordUtil.isValidPassword(pwd[, max])` | `PasswordPolicy.DEFAULT.check(pwd).isValid()` | Default minimum 12 (old: 8) and maximum 128 (old: 10). Reports which rules failed. Symbols are any visible punctuation or symbol (old: only `@#$%^&+=-_*`). No-break spaces count as whitespace, and control, format and other invisible characters are rejected. Unicode-aware letter and digit tests. |
| `PasswordUtil.hasNumbers` / `hasLowerCase` / `hasUpperCase` / `hasSpecialCharCase` | `PasswordPolicy.check(...).violations()` | Inspect `MISSING_DIGIT`, `MISSING_LOWER`, `MISSING_UPPER`, `MISSING_SPECIAL`, or build a `PasswordPolicy` that requires only some `CharacterClass`es. |
| `PrettyDate.pretty(date)` | `RelativeTime.between(instant, now).toEnglish()` | Takes `Instant`s instead of `Date` and an implicit "now". Fixes "n Yesterday" for 2 to 6 days, adds months (capped at 11), years and future instants. Weeks are floored (old: rounded up, so 8 days was "2 weeks ago"). |
| `CronUtil.convertDateToCronExpression(date)` | `CronExpressions.quartzAt(localDateTime)` | Takes `LocalDateTime` instead of `Date` in the JVM zone. Rejects years outside 1970 to 2099, which Quartz cannot schedule. |
| `FileUtil.extractExtension(name)` | `FileNames.extension(name)` | Returns `Optional<String>` without the dot (old: the dot included, and an exception when there was no dot). Only the last path segment is considered; `.bashrc` has no extension. Only 1 to 16 letters, digits, `-` or `_` count as an extension; anything else (NUL, trailing space, `:stream`) yields empty. |
| `FileUtil.replaceFilename(name, base)` | `FileNames.withBaseName(name, base)` | Rejects a `base` that is blank, `.` or `..`, contains a separator, colon, control or format character, or ends in a dot or space; drops an extension that is not plain; works when there is no extension. |
| `StreamUtil.distinctByKey(extractor)` | `Predicates.distinctByKey(extractor)` | Accepts `null` keys (old: `NullPointerException`). |

## Dropped, use the JDK

| Old | Replacement |
|---|---|
| `Util.isNull(o)` | `Objects.isNull(o)` |
| `Util.ifNull(param, result)` | `Objects.requireNonNullElse(param, result)` (the old method returned its arguments the wrong way round) |
| `Util.uuid()` / `uuid(prefix)` | `UUID.randomUUID().toString()` |
| `Util.trimToLowerCase` / `trimToUpperCase` | `text.trim().replaceAll("\\s", "").toLowerCase(Locale.ROOT)` (`trim()` also removes control characters at the ends that `\s` does not match) |
| `DateUtil` format constants, `getDateFormat`, `getFormat`, `getParse`, `getDate*`, `getDateToString*` | `DateTimeFormatter.ofPattern(...)` with `LocalDate`, `LocalDateTime` or `Instant` (`SimpleDateFormat` is not thread-safe, and parse errors returned `null`) |
| `DateUtil.compareTo(...)` | `Comparable.compareTo` on `java.time` types, or `truncatedTo(...)` before comparing |
| `DateUtil.parseDate(millis)` | `Instant.ofEpochMilli(Long.parseLong(value))` |
| `DateUtil.dateAddDays(days[, date])` | `LocalDate.plusDays(days)` / `Instant.plus(days, ChronoUnit.DAYS)` |
| `PrettyDate.subtractDateToSeconds(a, b)` | `Duration.between(b, a).toSeconds()` |
| `FileUtil.Image.encodeToString(file / bytes)` | `Base64.getEncoder().encodeToString(Files.readAllBytes(path))` |
| `FileUtil.Image.decodeToFile(name, base64)` | `Files.write(path, Base64.getDecoder().decode(base64))` |

## Not migrated

| Old | Why |
|---|---|
| `ErrorException` | Exception hierarchies belong to each service's domain. |
| `BigDecimalMoneySerializer` | Jackson-specific, and its `DecimalFormat` depended on the JVM locale. Format money in the presentation layer, or with a Jackson 3 serializer in the service. |
