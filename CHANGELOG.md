# Changelog

Each module keeps its own changelog; this file indexes them and records changes to the build itself.
Releases are tagged `sdk-<module>/vX.Y.Z` (`bom/vX.Y.Z` for the BOM) by the run that publishes them.

## Modules

| Module | Current | Changelog | Migration |
|---|---|---|---|
| `sdk-bom` | 1.0.0 | [below](#sdk-bom-100) | — |
| `sdk-user-security` | 3.0.0 | [CHANGELOG](security/sdk-user-security/CHANGELOG.md) | in the changelog |
| `sdk-user-security-authz` | 3.0.0 | [CHANGELOG](security/sdk-user-security-authz/CHANGELOG.md) | in the changelog |
| `sdk-tracing` | 1.0.0 | [CHANGELOG](sdk-tracing/CHANGELOG.md) | in the changelog |
| `sdk-event-messaging-core` | 6.0.0 | [CHANGELOG](messaging/sdk-event-messaging-core/CHANGELOG.md) | [6.0](messaging/MIGRATION-6.0.md) |
| `sdk-event-messaging-jdbc` | 6.0.0 | [CHANGELOG](messaging/sdk-event-messaging-jdbc/CHANGELOG.md) | [6.0](messaging/MIGRATION-6.0.md) |
| `sdk-event-messaging-rabbit` | 6.0.0 | [CHANGELOG](messaging/sdk-event-messaging-rabbit/CHANGELOG.md) | [6.0](messaging/MIGRATION-6.0.md) |
| `sdk-event-messaging-kafka` | 6.0.0 | [CHANGELOG](messaging/sdk-event-messaging-kafka/CHANGELOG.md) | [6.0](messaging/MIGRATION-6.0.md) |
| `sdk-file-share` | 2.0.0 | [CHANGELOG](sdk-file-share/CHANGELOG.md) | [2.0](sdk-file-share/MIGRATION-2.0.md) |
| `sdk-audit-log` | 3.0.0 | [CHANGELOG](sdk-audit-log/CHANGELOG.md) | [3.0](sdk-audit-log/MIGRATION-3.0.md) |
| `sdk-measure-micrometer` | 3.0.0 | [CHANGELOG](sdk-measure-micrometer/CHANGELOG.md) | [3.0](sdk-measure-micrometer/MIGRATION-3.0.md) |
| `sdk-parameter` | 2.0.0 | [CHANGELOG](sdk-parameter/CHANGELOG.md) | [2.0](sdk-parameter/MIGRATION-2.0.md) |
| `sdk-i18n` | 1.0.0 | [CHANGELOG](sdk-i18n/CHANGELOG.md) | [1.0](sdk-i18n/MIGRATION-1.0.md) |
| `sdk-totp` | 2.0.0 | [CHANGELOG](sdk-totp/CHANGELOG.md) | [2.0](sdk-totp/MIGRATION-2.0.md) |
| `sdk-commons` | 1.0.0 | [CHANGELOG](sdk-commons/CHANGELOG.md) | [from commons-support](sdk-commons/MIGRATION-from-commons-support.md) |
| `sdk-web-errors` | 1.0.0 | [CHANGELOG](sdk-web-errors/CHANGELOG.md) | [from commons-support](sdk-web-errors/MIGRATION-from-commons-support.md) |
| `sdk-device` | 1.0.0 | [CHANGELOG](sdk-device/CHANGELOG.md) | [from commons-support](sdk-device/MIGRATION-from-commons-support.md) |
| `sdk-barcode` | 1.0.0 | [CHANGELOG](sdk-barcode/CHANGELOG.md) | [from commons-support](sdk-barcode/MIGRATION-from-commons-support.md) |
| `sdk-support` | 1.0.0 | internal; no public API | — |
| `sdk-geo-validation` | 0.2.0 (published; 0.2.1-SNAPSHOT local) | leaving the SDK, see [HANDOFF](sdk-geo-validation/HANDOFF.md) | — |

## sdk-bom 1.0.0

- First release. Constrains every released SDK module, `sdk-support` included, to the versions
  above. Does not export Spring Boot's BOM ([ADR 0006](docs/adr/0006-sdk-bom-and-no-exported-boot-bom.md)).
- Includes `sdk-commons` 1.0.0 ([ADR 0010](docs/adr/0010-sdk-commons-for-framework-free-helpers.md)).
- Includes `sdk-web-errors` 1.0.0 ([ADR 0011](docs/adr/0011-problem-details-with-stable-error-codes.md)).
- Includes `sdk-device` 1.0.0 ([ADR 0012](docs/adr/0012-trusted-proxy-client-ip.md)).
- Includes `sdk-barcode` 1.0.0.
- `sdk-geo-validation` is not part of it ([ADR 0003](docs/adr/0003-retire-geo-validation-from-the-sdk.md)).

## Build

- Monorepo: the ten `vitxo-sdk-*` libraries move from `com.soldife.vitxo` to `io.codehunters` under
  one Gradle build, with conventions in `build-logic/` and versions in `gradle/libs.versions.toml`.
- JaCoCo gates are fixed for every module (LINE 0.90, BRANCH 0.80); `verifyCoverageGates` rejects
  per-module overrides.
- `verifyBomConstraints` keeps `sdk-bom` in step with the released modules; exclusions are explicit.
- `verifyBomLock` fails when a library version changes under an unchanged `sdk-bom` version
  (`platform/sdk-bom/bom-lock.properties`, rewritten with `:sdk-bom:updateBomLock`).
- Publishing skips versions already in GitHub Packages and `-SNAPSHOT`s, then tags exactly the
  modules the run uploaded, from a separate job; publish runs never overlap.
