# java-sdk

Gradle multi-module monorepo for Codehunters' Spring Boot SDK libraries. GroupId `io.codehunters`.

Requires Spring Boot 4 and Java 21 ([ADR 0007](docs/adr/0007-support-spring-boot-4-only.md)). Decisions are recorded in [`docs/adr`](docs/adr/README.md).

Migrated from the standalone `vitxo-sdk-*` repositories (org `SOLDIFE`, group `com.soldife.vitxo`). Package/group renamed, no git history carried over, `@ConfigurationProperties` prefixes left unchanged for consumer backward-compatibility.

## Modules

Modules that belong to one family live in a grouped directory (`messaging/`, `security/`,
`platform/`); the rest sit at the root. Directories don't change project or artifact names: every
artifact is `io.codehunters:sdk-*`. Build conventions live in `build-logic/` and versions in
`gradle/libs.versions.toml`.

| Module | Package | Status |
|---|---|---|
| `sdk-utils` | `io.codehunters.totp` | pending |
| `sdk-measure-micrometer` | `io.codehunters.measure` | pending |
| `sdk-geo-validation` | `io.codehunters.geo` | pending |
| `sdk-i18n` | `io.codehunters.i18n` | pending |
| `sdk-tracing` | `io.codehunters.tracing` | 1.0.0 — [README](sdk-tracing/README.md) |
| `sdk-parameter` | `io.codehunters.parameter` | pending |
| `sdk-audit-log` | `io.codehunters.audit` | 3.0.0 — [README](sdk-audit-log/README.md) |
| `sdk-user-security` | `io.codehunters.security` | 3.0.0 — [README](security/sdk-user-security/README.md) |
| `sdk-user-security-authz` | `io.codehunters.security.authz` | 3.0.0 — [README](security/sdk-user-security-authz/README.md) |
| `sdk-file-share` | `io.codehunters.fileshare` | pending |
| `sdk-event-messaging-core` | `io.codehunters.messaging` | 6.0.0 — [README](messaging/sdk-event-messaging-core/README.md) |
| `sdk-event-messaging-jdbc` | `io.codehunters.messaging` | 6.0.0 — [README](messaging/sdk-event-messaging-jdbc/README.md) |
| `sdk-event-messaging-rabbit` | `io.codehunters.messaging` | 6.0.0 — [README](messaging/sdk-event-messaging-rabbit/README.md) |
| `sdk-event-messaging-kafka` | `io.codehunters.messaging` | 6.0.0 — [README](messaging/sdk-event-messaging-kafka/README.md) |
| `sdk-support` | `io.codehunters.support` | internal |
| `sdk-bom` | — (BOM, `java-platform`) | pending |

## Build

```bash
./gradlew build
```

## Publish

Publishes to GitHub Packages (`https://maven.pkg.github.com/codehunters/java-sdk`) on merge to `main`. Requires `GH_PACKAGES_USERNAME` / `GH_PACKAGES_TOKEN` env vars locally.

## Quality gates

- Spotless (`googleJavaFormat`) — `./gradlew spotlessCheck` / `spotlessApply`
- JaCoCo — LINE ≥ 90% and BRANCH ≥ 80% per module, enforced on `check` (a module may lower the branch bar temporarily with `coverage.branch.minimum` in its `gradle.properties`)
- Integration tests (`*IT`) use Testcontainers, so `./gradlew build` needs a running Docker daemon
- SonarCloud — single project `codehunters_java-sdk`, org `codehunters`
