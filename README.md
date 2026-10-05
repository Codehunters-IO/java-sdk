# java-sdk

Gradle multi-module monorepo for Codehunters' Spring Boot SDK libraries. GroupId `io.codehunters`.

Migrated from the standalone `vitxo-sdk-*` repositories (org `SOLDIFE`, group `com.soldife.vitxo`). Package/group renamed, no git history carried over, `@ConfigurationProperties` prefixes left unchanged for consumer backward-compatibility.

## Modules

| Module | Package | Status |
|---|---|---|
| `sdk-utils` | `io.codehunters.totp` | pending |
| `sdk-measure-micrometer` | `io.codehunters.measure` | pending |
| `sdk-geo-validation` | `io.codehunters.geo` | pending |
| `sdk-i18n` | `io.codehunters.i18n` | pending |
| `sdk-tracing` | `io.codehunters.tracing` | pending |
| `sdk-parameter` | `io.codehunters.parameter` | pending |
| `sdk-audit-log` | `io.codehunters.audit` | pending |
| `sdk-user-security` | `io.codehunters.sdk.security` | pending |
| `sdk-file-share` | `io.codehunters.fileshare` | pending |
| `sdk-event-messaging` | `io.codehunters.messaging` | pending |
| `sdk-bom` | — (BOM, `java-platform`) | pending |

## Build

```bash
./gradlew build
```

## Publish

Publishes to GitHub Packages (`https://maven.pkg.github.com/codehunters/java-sdk`) on merge to `main`. Requires `GH_PACKAGES_USERNAME` / `GH_PACKAGES_TOKEN` env vars locally.

## Quality gates

- Spotless (`googleJavaFormat`) — `./gradlew spotlessCheck` / `spotlessApply`
- JaCoCo — LINE coverage ≥ 90% per module, enforced on `check`
- SonarCloud — single project `codehunters_java-sdk`, org `codehunters`
