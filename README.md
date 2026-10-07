# java-sdk

Gradle multi-module monorepo for Codehunters' Spring Boot SDK libraries. GroupId `io.codehunters`.

Requires Spring Boot 4 (built and tested against 4.1.1) and Java 21 ([ADR 0007](docs/adr/0007-support-spring-boot-4-only.md)). Decisions are recorded in [`docs/adr`](docs/adr/README.md).

Migrated from the standalone `vitxo-sdk-*` repositories (org `SOLDIFE`, group `com.soldife.vitxo`). Package/group renamed, no git history carried over. Configuration properties move to the `codehunters.<sdk>.*` prefix; the old keys keep working, with a deprecation warning, until the next major version of each SDK (see [Migrating a service](#migrating-a-service)).

## Modules

Modules that belong to one family live in a grouped directory (`messaging/`, `security/`,
`platform/`); the rest sit at the root. Directories don't change project or artifact names: every
artifact is `io.codehunters:sdk-*`. Build conventions live in `build-logic/` and versions in
`gradle/libs.versions.toml`.

| Module | Package | Version | Docs |
|---|---|---|---|
| `sdk-bom` | — (BOM, `java-platform`) | 1.0.0 | [below](#using-the-bom) |
| `sdk-user-security` | `io.codehunters.security` | 3.1.0 | [README](security/sdk-user-security/README.md) · [CHANGELOG](security/sdk-user-security/CHANGELOG.md) |
| `sdk-user-security-authz` | `io.codehunters.security.authz` | 3.0.0 | [README](security/sdk-user-security-authz/README.md) · [CHANGELOG](security/sdk-user-security-authz/CHANGELOG.md) |
| `sdk-tracing` | `io.codehunters.tracing` | 1.0.0 | [README](sdk-tracing/README.md) · [CHANGELOG](sdk-tracing/CHANGELOG.md) |
| `sdk-event-messaging-core` | `io.codehunters.messaging` | 6.0.0 | [README](messaging/sdk-event-messaging-core/README.md) · [MIGRATION](messaging/MIGRATION-6.0.md) |
| `sdk-event-messaging-jdbc` | `io.codehunters.messaging` | 6.0.0 | [README](messaging/sdk-event-messaging-jdbc/README.md) |
| `sdk-event-messaging-rabbit` | `io.codehunters.messaging` | 6.0.0 | [README](messaging/sdk-event-messaging-rabbit/README.md) |
| `sdk-event-messaging-kafka` | `io.codehunters.messaging` | 6.0.0 | [README](messaging/sdk-event-messaging-kafka/README.md) |
| `sdk-file-share` | `io.codehunters.fileshare` | 2.0.0 | [README](sdk-file-share/README.md) · [MIGRATION](sdk-file-share/MIGRATION-2.0.md) |
| `sdk-audit-log` | `io.codehunters.audit` | 3.0.0 | [README](sdk-audit-log/README.md) · [MIGRATION](sdk-audit-log/MIGRATION-3.0.md) |
| `sdk-measure-micrometer` | `io.codehunters.measure` | 3.0.0 | [README](sdk-measure-micrometer/README.md) · [MIGRATION](sdk-measure-micrometer/MIGRATION-3.0.md) |
| `sdk-parameter` | `io.codehunters.parameter` | 2.0.0 | [README](sdk-parameter/README.md) · [MIGRATION](sdk-parameter/MIGRATION-2.0.md) |
| `sdk-i18n` | `io.codehunters.i18n` | 1.0.0 | [README](sdk-i18n/README.md) · [MIGRATION](sdk-i18n/MIGRATION-1.0.md) |
| `sdk-totp` (was `sdk-utils`) | `io.codehunters.totp` | 2.0.0 | [README](sdk-totp/README.md) · [MIGRATION](sdk-totp/MIGRATION-2.0.md) |
| `sdk-commons` | `io.codehunters.commons` | 1.0.0 | [README](sdk-commons/README.md) · [CHANGELOG](sdk-commons/CHANGELOG.md) |
| `sdk-web-errors` | `io.codehunters.weberrors` | 1.0.0 | [README](sdk-web-errors/README.md) · [MIGRATION](sdk-web-errors/MIGRATION-from-commons-support.md) |
| `sdk-device` | `io.codehunters.device` | 1.0.0 | [README](sdk-device/README.md) · [MIGRATION](sdk-device/MIGRATION-from-commons-support.md) |
| `sdk-barcode` | `io.codehunters.barcode` | 1.0.0 | [README](sdk-barcode/README.md) · [MIGRATION](sdk-barcode/MIGRATION-from-commons-support.md) |
| `sdk-jasper` | `io.codehunters.jasper` | 1.0.0 | [README](sdk-jasper/README.md) · [MIGRATION](sdk-jasper/MIGRATION-from-commons-support.md) |
| `sdk-jpa` | `io.codehunters.jpa` | 1.0.0 | [README](sdk-jpa/README.md) · [MIGRATION](sdk-jpa/MIGRATION-from-commons-support.md) |
| `sdk-support` | `io.codehunters.support` | 1.0.0, internal | Legacy property aliases; brought in transitively |
| `sdk-geo-validation` | `io.codehunters.geo` | 0.2.0 (published; 0.2.1-SNAPSHOT local), leaving the SDK | [handoff](sdk-geo-validation/HANDOFF.md) ([ADR 0003](docs/adr/0003-retire-geo-validation-from-the-sdk.md)); not in the BOM |

Release notes for every module are indexed in [CHANGELOG.md](CHANGELOG.md).

## Using the BOM

`sdk-bom` aligns every SDK module, `sdk-support` included. It does not export Spring Boot's BOM
([ADR 0006](docs/adr/0006-sdk-bom-and-no-exported-boot-bom.md)): keep using Boot's dependency
management for Spring itself.

Gradle:

```kotlin
dependencies {
    implementation(platform("io.codehunters:sdk-bom:1.0.0"))
    implementation("io.codehunters:sdk-user-security")
    implementation("io.codehunters:sdk-event-messaging-jdbc")
    implementation("io.codehunters:sdk-event-messaging-rabbit")
}
```

Maven:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.codehunters</groupId>
      <artifactId>sdk-bom</artifactId>
      <version>1.0.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Maven consumers should import the BOM: SDK modules publish their resolved dependency versions,
but Maven's nearest-wins mediation can still mix versions of `sdk-support` and
`sdk-event-messaging-core` when two SDK modules are on the classpath.

## Migrating a service

Do it one module at a time; each module's `MIGRATION-*.md` (or CHANGELOG, for security and tracing)
has the full checklist. What every service has to look at:

1. **Coordinates.** Import `sdk-bom:1.0.0` and drop explicit SDK versions.

   | Before | Now |
   |---|---|
   | `sdk-event-messaging:5.x` | `sdk-event-messaging-jdbc` + `-rabbit` or `-kafka` 6.0.0 (`-core` alone for the model) |
   | `sdk-user-security:2.x` with the policy enforcer | `sdk-user-security` + `sdk-user-security-authz` 3.0.0 |
   | `sdk-utils:1.x` | `sdk-totp:2.0.0` |
   | `sdk-geo-validation:0.2.0` | stays where it is until the raffle service adopts the code |

2. **Duplicate classes (Maven only).** `sdk-event-messaging-core` replaces `sdk-event-messaging` and
   `sdk-totp` replaces `sdk-utils` through Gradle capabilities, so Gradle fails the build when the
   old artifact is still on the graph. Maven does not check capabilities: add an `<exclusion>` for
   `io.codehunters:sdk-event-messaging` and `io.codehunters:sdk-utils` to any dependency that still
   brings them.
3. **Imports.** Only security moves: `io.codehunters.sdk.security.*` → `io.codehunters.security.*`
   and `io.codehunters.sdk.security.authz.*` → `io.codehunters.security.authz.*`. Tracing constants
   move to `TraceHeaders`. Every other package is unchanged.
4. **Properties.** Rename to the `codehunters.<sdk>.*` prefix
   ([ADR 0004](docs/adr/0004-single-codehunters-property-prefix.md)). Old keys, in files or
   environment variables, keep working with a startup warning until each SDK's next major version.

   | Before | Now |
   |---|---|
   | `keycloak.security.*` | `codehunters.security.*` |
   | `keycloak.policy-enforcer.*` | `codehunters.security.authz.*` |
   | `keycloak.host`, `keycloak.realm` | `codehunters.security.url`, `codehunters.security.realm` |
   | `outbox.*` | `codehunters.outbox.*` |
   | `vitxo.file-share.*` | `codehunters.file-share.*` |
   | `vitxo.audit.*` | `codehunters.audit.*` |
   | `vitxo.i18n.*` | `codehunters.i18n.*` |
   | `vitxo.totp.*` | `codehunters.totp.*` |

   Only the documented old keys are aliased, and the new key wins when both are set.
5. **DDL.** Apply before deploying:
   - Outbox on SQL Server: the `seq` column, **before** 6.0 goes out
     ([messaging migration](messaging/MIGRATION-6.0.md#ddl)).
   - Audit log: the Flyway script `V1__create_audit_event_table.sql` no longer ships; copy it or
     ignore the missing migration ([audit migration](sdk-audit-log/MIGRATION-3.0.md)). Reference DDL
     for PostgreSQL and SQL Server ships under `db/migration/audit/<database>/`.
6. **Broker settings.**
   - RabbitMQ: `spring.rabbitmq.publisher-confirm-type=correlated` (or `simple`) and
     `spring.rabbitmq.publisher-returns=true`.
   - Kafka: a `ProducerFactory` bean; `codehunters.outbox.publisher.kafka.send-timeout` defaults to 5 s.
   - Both: consumers deduplicate on the `outbox-message-id` header.
7. **Security.** `codehunters.security.audiences` is required (or `allow-any-audience=true`),
   introspection `fail-open` now defaults to `false`, and gateway identity headers must agree with the token.
8. **Dashboards.** `sdk-measure-micrometer` renames its meters
   ([table](sdk-measure-micrometer/MIGRATION-3.0.md)); audit `errorMessage` holds exception class
   names.

## Build

```bash
./gradlew build
./gradlew -p build-logic test   # convention plugin tests
```

Integration tests (`*IT`) use Testcontainers, so `./gradlew build` needs a running Docker daemon.

## Publish

Publishes to GitHub Packages (`https://maven.pkg.github.com/Codehunters-IO/java-sdk`) on merge to
`master` (the base branch; `main` also triggers it). A version is published once: versions already in the repository and `-SNAPSHOT` versions
are skipped. Each module the run actually uploads is then tagged `sdk-<module>/vX.Y.Z`
(`bom/vX.Y.Z` for the BOM) at the commit that built it, by a separate job that alone can write to
the repository; existing tags are left alone, and runs never overlap. Publishing locally needs
`GH_PACKAGES_USERNAME` / `GH_PACKAGES_TOKEN`.

## Quality gates

- Spotless (`googleJavaFormat`) — `./gradlew spotlessCheck` / `spotlessApply`
- JaCoCo — LINE ≥ 90% and BRANCH ≥ 80% per module, enforced on `check`. The gates are the same for
  every module: `verifyCoverageGates` fails the build if `coverage.branch.minimum` is set.
- BOM — `verifyBomConstraints` fails the build when a released module is missing from `sdk-bom`, or
  a `-SNAPSHOT` or excluded one (`sdk-geo-validation`) is listed. `verifyBomLock` fails when a
  library version changes while the BOM version doesn't: bump `sdk-bom` and run
  `./gradlew :sdk-bom:updateBomLock`, which records the versions in
  `platform/sdk-bom/bom-lock.properties`.
- SonarCloud — single project `codehunters_java-sdk`, org `codehunters`

See [CONTRIBUTING.md](CONTRIBUTING.md) for branches, commits and the definition of done.
