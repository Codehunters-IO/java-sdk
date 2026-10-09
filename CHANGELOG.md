# Changelog

Each module keeps its own changelog; this file indexes them and records changes to the build itself.
Releases are tagged `sdk-<module>/vX.Y.Z` (`bom/vX.Y.Z` for the BOM) by the run that publishes them.

## Modules

| Module | Current | Changelog | Migration |
|---|---|---|---|
| `sdk-bom` | 1.5.0 | [below](#sdk-bom-150) | — |
| `sdk-user-security` | 3.1.0 | [CHANGELOG](security/sdk-user-security/CHANGELOG.md) | in the changelog |
| `sdk-user-security-authz` | 3.0.0 | [CHANGELOG](security/sdk-user-security-authz/CHANGELOG.md) | in the changelog |
| `sdk-tracing` | 1.0.0 | [CHANGELOG](sdk-tracing/CHANGELOG.md) | in the changelog |
| `sdk-event-messaging-core` | 7.0.0 | [CHANGELOG](messaging/sdk-event-messaging-core/CHANGELOG.md) | [6.0](messaging/MIGRATION-6.0.md) · [7.0](messaging/MIGRATION-7.0.md) |
| `sdk-event-messaging-jdbc` | 7.0.0 | [CHANGELOG](messaging/sdk-event-messaging-jdbc/CHANGELOG.md) | [6.0](messaging/MIGRATION-6.0.md) · [7.0](messaging/MIGRATION-7.0.md) |
| `sdk-event-messaging-rabbit` | 7.0.0 | [CHANGELOG](messaging/sdk-event-messaging-rabbit/CHANGELOG.md) | [6.0](messaging/MIGRATION-6.0.md) · [7.0](messaging/MIGRATION-7.0.md) |
| `sdk-event-messaging-kafka` | 7.0.0 | [CHANGELOG](messaging/sdk-event-messaging-kafka/CHANGELOG.md) | [6.0](messaging/MIGRATION-6.0.md) · [7.0](messaging/MIGRATION-7.0.md) |
| `sdk-file-share` | 2.0.0 | [CHANGELOG](sdk-file-share/CHANGELOG.md) | [2.0](sdk-file-share/MIGRATION-2.0.md) |
| `sdk-audit-log` | 3.1.0 | [CHANGELOG](sdk-audit-log/CHANGELOG.md) | [3.0](sdk-audit-log/MIGRATION-3.0.md) |
| `sdk-measure-micrometer` | 3.0.0 | [CHANGELOG](sdk-measure-micrometer/CHANGELOG.md) | [3.0](sdk-measure-micrometer/MIGRATION-3.0.md) |
| `sdk-parameter` | 2.0.0 | [CHANGELOG](sdk-parameter/CHANGELOG.md) | [2.0](sdk-parameter/MIGRATION-2.0.md) |
| `sdk-commons` | 1.1.0 | [CHANGELOG](sdk-commons/CHANGELOG.md) | [1.1](sdk-commons/MIGRATION-1.1.md) · [from commons-support](sdk-commons/MIGRATION-from-commons-support.md) |
| `sdk-web` | 1.0.0 | [CHANGELOG](sdk-web/CHANGELOG.md) | [1.0](sdk-web/MIGRATION-1.0.md) |
| `sdk-barcode` | 1.0.0 | [CHANGELOG](sdk-barcode/CHANGELOG.md) | [from commons-support](sdk-barcode/MIGRATION-from-commons-support.md) |
| `sdk-jasper` | 1.0.0 | [CHANGELOG](sdk-jasper/CHANGELOG.md) | [from commons-support](sdk-jasper/MIGRATION-from-commons-support.md) |
| `sdk-jpa` | 1.0.0 | [CHANGELOG](sdk-jpa/CHANGELOG.md) | [from commons-support](sdk-jpa/MIGRATION-from-commons-support.md) |
| `sdk-support` | 1.0.0 | internal; no public API | — |
| `sdk-geo-validation` | 1.1.0 | [CHANGELOG](sdk-geo-validation/CHANGELOG.md) | [1.0](sdk-geo-validation/MIGRATION-1.0.md) |

## sdk-bom 1.5.0

- Includes `sdk-geo-validation` 1.1.0, which adds a PostGIS adapter
  ([ADR 0019](docs/adr/0019-postgis-geofence-adapter.md)).

## sdk-bom 1.4.0

- Includes `sdk-audit-log` 3.1.0, which takes the actor of a `Mono` or `Flux` from the subscriber's
  Reactor context.

## sdk-bom 1.3.0

- Includes `sdk-event-messaging-core`, `-jdbc`, `-rabbit` and `-kafka` 7.0.0, which charge a reclaim
  attempt only to rows whose send had started
  ([ADR 0018](docs/adr/0018-charge-attempts-only-for-started-sends.md)).

## sdk-bom 1.2.0

- Includes `sdk-web` 1.0.0, which replaces `sdk-web-errors`, `sdk-device` and `sdk-i18n`, and
  `sdk-commons` 1.1.0, which absorbs `sdk-totp`
  ([ADR 0016](docs/adr/0016-consolidate-web-and-commons-artifacts.md)).
- No longer lists `sdk-web-errors`, `sdk-device`, `sdk-i18n` or `sdk-totp`.

## sdk-bom 1.1.0

- Includes `sdk-geo-validation` 1.0.0, now domain-agnostic geofencing
  ([ADR 0015](docs/adr/0015-domain-agnostic-geofencing.md), superseding ADR 0003).

## sdk-bom 1.0.0

- First release. Constrains every released SDK module, `sdk-support` included, to the versions
  above. Does not export Spring Boot's BOM ([ADR 0006](docs/adr/0006-sdk-bom-and-no-exported-boot-bom.md)).
- Includes `sdk-commons` 1.0.0 ([ADR 0010](docs/adr/0010-sdk-commons-for-framework-free-helpers.md)).
- Includes `sdk-web-errors` 1.0.0 ([ADR 0011](docs/adr/0011-problem-details-with-stable-error-codes.md)).
- Includes `sdk-device` 1.0.0 ([ADR 0012](docs/adr/0012-trusted-proxy-client-ip.md)).
- Includes `sdk-barcode` 1.0.0.
- Includes `sdk-jasper` 1.0.0 ([ADR 0014](docs/adr/0014-jasper-reports-from-the-classpath-only.md)).
- Includes `sdk-jpa` 1.0.0 ([ADR 0013](docs/adr/0013-jpa-building-blocks.md)).
- `sdk-geo-validation` was not part of it ([ADR 0003](docs/adr/0003-retire-geo-validation-from-the-sdk.md), since superseded).

## Build

- Monorepo: the ten `vitxo-sdk-*` libraries move from `com.soldife.vitxo` to `io.codehunters` under
  one Gradle build, with conventions in `build-logic/` and versions in `gradle/libs.versions.toml`.
- JaCoCo gates are fixed for every module (LINE 0.90, BRANCH 0.80); `verifyCoverageGates` rejects
  per-module overrides.
- `verifyBomConstraints` keeps `sdk-bom` in step with the released modules; exclusions are explicit.
- `verifyBomLock` fails when a library version changes under an unchanged `sdk-bom` version
  (`platform/sdk-bom/bom-lock.properties`, rewritten with `:sdk-bom:updateBomLock`).
- `codehuntersPublishing { replaces(...) }` takes several artifacts at once.
- Publishing skips versions already in GitHub Packages and `-SNAPSHOT`s, then tags exactly the
  modules the run uploaded, from a separate job; publish runs never overlap.
- Built against Spring Boot 4.1.1 (Spring 7.0.9, Security 7.1.1). The internal platform raises
  Jackson 3 to 3.1.7, Jackson 2 to 2.21.7 and Tomcat to 11.0.26, past advisories open in the
  versions Boot manages.
- Publishing points at `Codehunters-IO/java-sdk`.
