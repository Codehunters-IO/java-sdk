# Migrating from codehuntersio-java-commons-support

`sdk-commons` replaces the loose utilities of `io.codehunters.commons:commons-util` (5.1.0). They
were rewritten, not copied: names, signatures and some behaviour changed. The other modules of
that repository (DTOs, JPA, web, JWT, properties, mail, Jasper, barcode, IP/device lookup, batch)
are not part of the SDK.

## Kept, with a new API

| Old | New | What changed |
|---|---|---|
| `MasqueradeUtil.mask(text, position, start, end[, wildcard])` | `Masking.keep(text, first, last[, mask])` | Counts of visible characters instead of indices and an `int` position. Never returns `null`; fully masks when the kept part covers the value; counts code points; invalid input throws `IllegalArgumentException` without the value. |
| `MasqueradeUtil.leftMask(text, start)` | `Masking.keepLast(text, count)` | Same result expressed as how many trailing characters stay visible. |
| `MasqueradeUtil.rightMask(text, end)` | `Masking.keepFirst(text, count)` | Same, for leading characters. |
| — | `Masking.email`, `Masking.pan` | New. |
| `StringSimplifier.simplifiedString(text)` | `TextNormalizer.simplify(text)` | Lower-cases with `Locale.ROOT` (the old one used the default locale). Every non-letter, non-digit character becomes `_` (the old one passed unknown symbols through), and `_` is trimmed at both ends (the old one only trimmed the end). Modifier letters such as the Japanese prolonged sound mark `ー` are kept (the old one removed them), and text is recomposed (NFC) after removing diacritics. Returns `""` instead of the original text when nothing survives, and throws instead of returning `null`. |
| `PasswordUtil.randomPass([length])` | `new PasswordGenerator().generate([length])` | Length between 12 and 128, default 16 (old: 10, and any length below 8 looped forever). Uses a shared `SecureRandom` instead of `getInstanceStrong()`, which can block. No checked exception. |
| `PasswordUtil.randomNumericPass([length])` | `new PasswordGenerator().numeric(length)` | Length between 4 and 128. |
| `PasswordUtil.isValidPassword(pwd[, max])` | `PasswordPolicy.DEFAULT.check(pwd).isValid()` | Default minimum 12 (old: 8) and maximum 128 (old: 10). Reports which rules failed. Symbols are any non-letter, non-digit, non-space character (old: only `@#$%^&+=-_*`). Unicode-aware letter and digit tests. |
| `PasswordUtil.hasNumbers` / `hasLowerCase` / `hasUpperCase` / `hasSpecialCharCase` | `PasswordPolicy.check(...).violations()` | Inspect `MISSING_DIGIT`, `MISSING_LOWER`, `MISSING_UPPER`, `MISSING_SPECIAL`, or build a `PasswordPolicy` that requires only some `CharacterClass`es. |
| `PrettyDate.pretty(date)` | `RelativeTime.between(instant, now).toEnglish()` | Takes `Instant`s instead of `Date` and an implicit "now". Fixes "n Yesterday" for 2 to 6 days, adds months, years and future instants. |
| `CronUtil.convertDateToCronExpression(date)` | `CronExpressions.quartzAt(localDateTime)` | Takes `LocalDateTime` instead of `Date` in the JVM zone. |
| `FileUtil.extractExtension(name)` | `FileNames.extension(name)` | Returns `Optional<String>` without the dot (old: the dot included, and an exception when there was no dot). Only the last path segment is considered; `.bashrc` has no extension. |
| `FileUtil.replaceFilename(name, base)` | `FileNames.withBaseName(name, base)` | Rejects blank names and path separators in `base`; works when there is no extension. |
| `StreamUtil.distinctByKey(extractor)` | `Predicates.distinctByKey(extractor)` | Accepts `null` keys (old: `NullPointerException`). |

## Dropped, use the JDK

| Old | Replacement |
|---|---|
| `Util.isNull(o)` | `Objects.isNull(o)` |
| `Util.ifNull(param, result)` | `Objects.requireNonNullElse(param, result)` (the old method returned its arguments the wrong way round) |
| `Util.uuid()` / `uuid(prefix)` | `UUID.randomUUID().toString()` |
| `Util.trimToLowerCase` / `trimToUpperCase` | `text.replaceAll("\\s", "").toLowerCase(Locale.ROOT)` |
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
