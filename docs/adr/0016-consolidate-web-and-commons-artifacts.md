---
status: accepted
date: 2026-10-08
decision-makers: [andres]
supersedes: 0005
amends: 0010
---

# 0016. Merge the servlet web modules into sdk-web and TOTP into sdk-commons

## Context and Problem Statement

The migration left 21 artifacts. Several are small and always travel together: `sdk-web-errors`
(problem details), `sdk-device` (client IP and User-Agent) and `sdk-i18n` (X-Locale and messages)
are all servlet/Spring MVC auto-configurations with the same `compileOnly` web stack, used by the
same HTTP services. `sdk-totp` is 350 lines of JDK-only crypto, next to the password helpers of
`sdk-commons`. Every extra artifact is one more coordinate in each service, one more BOM entry and
one more release to track.

No service has moved to the new artifacts yet (the `MIGRATION-*.md` guides are still pending), so
changing coordinates now costs nothing; after the first migrations every merge would be another
breaking change for consumers.

## Decision Drivers

- Merge modules that share their dependency footprint and their consumers.
- Keep heavy or specialised third-party dependencies out of services that do not need them.
- Do not break code: packages, property prefixes and legacy aliases stay as they are.

## Considered Options

- Option A — `sdk-web` (web-errors + device + i18n) and `sdk-commons` absorbing `sdk-totp`.
- Option B — One `sdk-web` only; TOTP stays on its own.
- Option C — Keep every module separate.

## Decision Outcome

Chosen option: **Option A**.

- `sdk-web` 1.0.0 contains `io.codehunters.weberrors`, `io.codehunters.device` and
  `io.codehunters.i18n`. Each keeps its own auto-configuration, properties
  (`codehunters.web-errors.*`, `codehunters.device.*`, `codehunters.i18n.*`) and switch
  (`<prefix>.enabled=false`), so a service can turn off the parts it does not use.
- `sdk-commons` 1.1.0 contains `io.codehunters.totp` next to its helpers. The helpers still depend
  on the JDK only (ArchUnit), and so does the TOTP core; only `io.codehunters.totp.config` touches
  Spring Boot, which stays `compileOnly`. `sdk-support` becomes a runtime dependency of
  `sdk-commons`, for the `vitxo.totp.*` aliases. This amends
  [ADR 0010](0010-sdk-commons-for-framework-free-helpers.md), which promised no auto-configuration.
- Both publish the Gradle capabilities of what they replace (`sdk-web-errors`, `sdk-device`,
  `sdk-i18n`; `sdk-totp`, `sdk-utils`), so a Gradle build resolving an old and a new artifact fails
  instead of loading the same classes twice. Maven consumers exclude the old artifacts.
- `sdk-bom` 1.2.0 lists `sdk-web` and `sdk-commons` 1.1.0 and drops the replaced modules.
- This supersedes [ADR 0005](0005-rename-sdk-utils-to-sdk-totp.md): the TOTP artifact is now
  `sdk-commons`, and the `sdk-utils` guard moves with it.

Stay separate:

| Module | Why |
|---|---|
| `sdk-barcode`, `sdk-jasper`, `sdk-jpa` | Heavy dependencies (ZXing, JasperReports + JDT, Hibernate) |
| `sdk-tracing` | Used by web and by RabbitMQ/messaging; inside `sdk-web` it would pull web into messaging |
| `sdk-parameter`, `sdk-geo-validation` | JDBC / SQL Server and a domain of their own |
| `sdk-measure-micrometer` | Micrometer and AOP, nothing in common with the web stack |
| `sdk-event-messaging-*`, `sdk-user-security*` | Split on purpose ([0001](0001-split-event-messaging-by-transport.md), [0002](0002-move-policy-enforcer-to-its-own-artifact.md)) |

### Consequences

- Good: 21 artifacts become 18; a typical HTTP service declares one web dependency instead of three.
- Good: imports and configuration do not change; only coordinates do.
- Bad: `sdk-web` releases as one unit, so a fix in i18n also bumps the error handling version.
- Bad: services that used only `sdk-device` (for example a batch job reading IPs) now carry the
  web-errors and i18n classes too; their auto-configurations stay inactive outside a servlet app
  or behind their switches.
- Neutral: `sdk-web-errors`, `sdk-device`, `sdk-i18n` 1.0.0 and `sdk-totp` 2.0.0 stay in GitHub
  Packages until someone removes them; nothing builds them any more.
