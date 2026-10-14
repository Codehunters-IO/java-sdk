# java-sdk

Gradle multi-module monorepo for Codehunters' Spring Boot SDK libraries. GroupId `io.codehunters`.

Requires Spring Boot 4 (built and tested against 4.1.1) and Java 21 ([ADR 0007](docs/adr/0007-support-spring-boot-4-only.md)). Decisions are recorded in [`docs/adr`](docs/adr/README.md).

Every configuration property lives under `codehunters.*`, and PostgreSQL is the only supported
database ([ADR 0021](docs/adr/0021-postgresql-only-and-codehunters-properties.md)).

## Modules

Modules that belong to one family live in a grouped directory (`messaging/`, `security/`,
`platform/`); the rest sit at the root. Directories don't change project or artifact names: every
artifact is `io.codehunters:sdk-*`. Build conventions live in `build-logic/` and versions in
`gradle/libs.versions.toml`.

| Module | Package | Version | Docs |
|---|---|---|---|
| `sdk-bom` | — (BOM, `java-platform`) | 7.1.0 | [below](#using-the-bom) |
| `sdk-user-security` | `io.codehunters.security` | 6.0.0 | [README](security/sdk-user-security/README.md) · [MIGRATION 6.0](security/sdk-user-security/MIGRATION-6.0.md) |
| `sdk-user-security-authz` | `io.codehunters.security.authz` | 6.0.0 | [README](security/sdk-user-security-authz/README.md) · [MIGRATION 6.0](security/sdk-user-security-authz/MIGRATION-6.0.md) |
| `sdk-tracing` | `io.codehunters.tracing` | 3.0.0 | [README](sdk-tracing/README.md) · [MIGRATION 3.0](sdk-tracing/MIGRATION-3.0.md) |
| `sdk-event-messaging-core` | `io.codehunters.messaging` | 9.1.0 | [README](messaging/sdk-event-messaging-core/README.md) · [MIGRATION 9.0](messaging/MIGRATION-9.0.md) |
| `sdk-event-messaging-jdbc` | `io.codehunters.messaging.jdbc` | 9.1.0 | [README](messaging/sdk-event-messaging-jdbc/README.md) |
| `sdk-event-messaging-rabbit` | `io.codehunters.messaging.rabbit` | 9.1.0 | [README](messaging/sdk-event-messaging-rabbit/README.md) |
| `sdk-event-messaging-kafka` | `io.codehunters.messaging.kafka` | 9.1.0 | [README](messaging/sdk-event-messaging-kafka/README.md) |
| `sdk-file-share` | `io.codehunters.fileshare` | 5.0.0 | [README](sdk-file-share/README.md) · [MIGRATION 5.0](sdk-file-share/MIGRATION-5.0.md) |
| `sdk-audit-log` | `io.codehunters.audit` | 5.2.0 | [README](sdk-audit-log/README.md) · [MIGRATION 5.0](sdk-audit-log/MIGRATION-5.0.md) |
| `sdk-measure-micrometer` | `io.codehunters.measure` | 4.0.0 | [README](sdk-measure-micrometer/README.md) · [MIGRATION 4.0](sdk-measure-micrometer/MIGRATION-4.0.md) |
| `sdk-parameter` | `io.codehunters.parameter` | 4.0.0 | [README](sdk-parameter/README.md) · [MIGRATION 4.0](sdk-parameter/MIGRATION-4.0.md) |
| `sdk-errors` | `io.codehunters.errors` | 1.0.0 | [README](sdk-errors/README.md) |
| `sdk-commons` (includes the former `sdk-totp`) | `io.codehunters.commons` (TOTP in `io.codehunters.commons.totp`) | 4.0.0 | [README](sdk-commons/README.md) · [MIGRATION 4.0](sdk-commons/MIGRATION-4.0.md) |
| `sdk-web` (former `sdk-web-errors`, `sdk-device`, `sdk-i18n`) | `io.codehunters.web.errors`, `io.codehunters.web.device`, `io.codehunters.web.i18n` | 4.0.0 | [README](sdk-web/README.md) · [MIGRATION 4.0](sdk-web/MIGRATION-4.0.md) |
| `sdk-barcode` | `io.codehunters.barcode` | 3.0.0 | [README](sdk-barcode/README.md) · [MIGRATION 3.0](sdk-barcode/MIGRATION-3.0.md) |
| `sdk-jasper` | `io.codehunters.jasper` | 2.1.0 | [README](sdk-jasper/README.md) · [MIGRATION 2.0](sdk-jasper/MIGRATION-2.0.md) |
| `sdk-jpa` | `io.codehunters.jpa` | 4.0.0 | [README](sdk-jpa/README.md) · [MIGRATION 4.0](sdk-jpa/MIGRATION-4.0.md) |
| `sdk-geo-validation` | `io.codehunters.geo` | 2.0.0 | [README](sdk-geo-validation/README.md) · [MIGRATION 2.0](sdk-geo-validation/MIGRATION-2.0.md) |

Packages follow one layout ([ADR 0020](docs/adr/0020-package-by-capability.md)): the module's root
package is its API, sub-packages are named by capability, `config` holds auto-configuration and
properties, and `internal` holds what is not API. Each module declares its root with
`codehuntersPackages { root = ... }` and `verifyPackageLayout` (part of `check`) enforces the layout.

Release notes for every module are indexed in [CHANGELOG.md](CHANGELOG.md).

## Using the BOM

`sdk-bom` aligns every SDK module. It does not export Spring Boot's BOM
([ADR 0006](docs/adr/0006-sdk-bom-and-no-exported-boot-bom.md)): keep using Boot's dependency
management for Spring itself.

Gradle:

```kotlin
dependencies {
    implementation(platform("io.codehunters:sdk-bom:7.1.0"))
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
      <version>7.1.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Maven consumers should import the BOM: SDK modules publish their resolved dependency versions,
but Maven's nearest-wins mediation can still mix versions of `sdk-commons` and
`sdk-event-messaging-core` when two SDK modules are on the classpath.

## Upgrading a service

Do it one module at a time; each module's `MIGRATION-*.md` has the full checklist. What every
service has to look at when moving to `sdk-bom` 5.0.0:

1. **Coordinates.** Import `sdk-bom:5.0.0` and drop explicit SDK versions. `sdk-web` replaces
   `sdk-web-errors`, `sdk-device` and `sdk-i18n`, and `sdk-commons` replaces `sdk-totp` and
   `sdk-utils`, through Gradle capabilities ([ADR 0016](docs/adr/0016-consolidate-web-and-commons-artifacts.md)).
   Maven does not check capabilities: exclude the old artifacts yourself. `sdk-support` is gone.
2. **Database.** PostgreSQL only. The outbox fails startup on any other database. Apply the
   PostgreSQL DDL shipped under each module's `db/migration/<module>/postgresql/` (outbox
   `send_started_at` and `traceparent`, audit table, parameters table) before deploying.
3. **Properties.** Only `codehunters.*` keys are read; there are no aliases for older prefixes
   ([ADR 0021](docs/adr/0021-postgresql-only-and-codehunters-properties.md)). `sdk-web` 3.0 moves
   its keys under `codehunters.web`:

   | Before | Now |
   |---|---|
   | `codehunters.web-errors.*` | `codehunters.web.errors.*` |
   | `codehunters.device.*` | `codehunters.web.device.*` |
   | `codehunters.i18n.*` | `codehunters.web.i18n.*` |

4. **Imports.** Packages follow [ADR 0020](docs/adr/0020-package-by-capability.md); each module's
   MIGRATION lists the moves.
5. **Broker settings.**
   - RabbitMQ: `spring.rabbitmq.publisher-confirm-type=correlated` (or `simple`) and
     `spring.rabbitmq.publisher-returns=true`.
   - Kafka: a `ProducerFactory` bean; `codehunters.outbox.publisher.kafka.send-timeout` defaults to 5 s.
   - Both: consumers deduplicate on the `outbox-message-id` header.
6. **Security.** `codehunters.security.audiences` is required (or `allow-any-audience=true`),
   introspection `fail-open` defaults to `false`, and gateway identity headers must agree with the
   token. `Authentication.getName()` is the user id.
7. **TOTP.** From `sdk-commons` 3.0.0 the defaults are HMAC-SHA1, 6 digits and 30 s, which
   authenticator apps expect, and the TOTP auto-configuration is opt-in: set
   `codehunters.totp.enabled=true`. Secrets created with the old SHA-512 default must keep
   `codehunters.totp.algorithm=hmac-sha512` ([commons 3.0](sdk-commons/MIGRATION-3.0.md)).
8. **Dashboards.** `sdk-measure-micrometer` renames its meters
   ([table](sdk-measure-micrometer/MIGRATION-3.0.md)); audit `errorMessage` holds exception class
   names.
9. **Client input errors.** An exception extending `sdk-errors`' `ClientInputException` answers
   400 with its stable `code`, also behind known wrappers such as
   `InvalidDataAccessApiUsageException`: `INVALID_SORT`, `INVALID_SEARCH` (`sdk-jpa`),
   `BARCODE_IMAGE_REJECTED`, `BARCODE_CONTENT_REJECTED` (`sdk-barcode`). Rejections raised while
   Spring binds the request keep the framework's generic 400 ([sdk-web](sdk-web/README.md)). Until
   `sdk-commons` 3.0.0 the class was `io.codehunters.commons.error.ClientInputException`
   ([ADR 0028](docs/adr/0028-error-contract-in-its-own-module.md)).
10. **Audit startup checks.** `@Auditable` methods with no publisher or no `aspectjweaver`, or with
    an unparseable expression, log an ERROR at startup, or fail it with
    `codehunters.audit.startup-check.fail-fast=true`; on servlet apps the actor defaults to the
    Spring Security user while a request is bound ([audit](sdk-audit-log/README.md)).
11. **Tracing.** `sdk-tracing` 3.0 builds on Micrometer Tracing
    ([ADR 0022](docs/adr/0022-tracing-on-micrometer-tracing.md)): add a tracing bridge
    (OpenTelemetry for the legacy gateway headers) and turn on Boot's observation for Rabbit and
    Kafka; the removed filter, task decorator and Rabbit instrumentation map to Boot properties in
    its [MIGRATION-3.0](sdk-tracing/MIGRATION-3.0.md). Legacy `x-trace-id` requests are sampled by
    trace-id ratio (`management.tracing.sampling.probability`), and without a bridge the trace id is
    empty in logs, problems and events. Outbox messages carry the writing request's validated
    `traceparent` ([ADR 0024](docs/adr/0024-outbox-carries-its-own-trace-context.md)).
12. **i18n.** `sdk-web` uses Boot's message source (`spring.messages.*`) and has no default locale;
    set `spring.web.locale=es` to keep the old default. Without an application bundle the SDK still
    registers a message source for its own error texts
    ([ADR 0025](docs/adr/0025-i18n-on-boot-message-source.md)).
13. **Authorization.** `sdk-user-security-authz` runs Keycloak's official policy enforcer with
    Keycloak's semantics (an unmapped path is denied under `ENFORCING`) on every request authenticated
    from an OAuth2 token, and answers 503 during `retry-backoff` after a failed enforcer build
    ([ADR 0023](docs/adr/0023-authz-on-keycloak-policy-enforcer.md),
    [MIGRATION-5.0](security/sdk-user-security-authz/MIGRATION-5.0.md)).
14. **Opt-in side effects.** JPA auditing (`codehunters.jpa.auditing.enabled`) and session
    introspection (`codehunters.security.introspection.enabled`) are off by default; with
    introspection off, a revoked session is accepted until its token expires. sdk-web device beans
    register in servlet applications only, and the outbox uses a private scheduler and clock
    ([ADR 0026](docs/adr/0026-developer-experience-defaults.md)).
15. **Audit expressions** run in a read-only SpEL sandbox: write `#order.id`, not `#order.getId()`
    ([MIGRATION-5.0](sdk-audit-log/MIGRATION-5.0.md)).
16. **Outbox writes** use `OutboxEvent.to(topic).key(k).type(t).payload(p)` and `OutboxWriter`;
    dispatch starts on commit and timing properties are `Duration`s. Per-key ordering needs one
    writer per key at a time ([MIGRATION-9.0](messaging/MIGRATION-9.0.md)).

## Build

```bash
./gradlew build
./gradlew -p build-logic test   # convention plugin tests
```

Integration tests (`*IT`) use Testcontainers, so `./gradlew build` needs a running Docker daemon.

## Releasing

Packages go to GitHub Packages (`https://maven.pkg.github.com/Codehunters-IO/java-sdk`) only when a
GitHub release is published ([ADR 0017](docs/adr/0017-publish-on-release-tags.md)). Merging to
`develop` or `main` publishes nothing.

1. **Merge.** The pull request to `develop` bumps the module's version and its `CHANGELOG.md` (and
   `sdk-bom`). A release pull request then merges `develop` into `main` with a merge commit.
2. **Draft.** `release-drafts.yml` builds the merge to `main` and drafts one release per module version that
   has no tag yet: `sdk-<module>/vX.Y.Z`, or `bom/vX.Y.Z`. The notes are that version's section of
   the module's `CHANGELOG.md`. A later merge moves an unpublished draft to the newer commit.
   `-SNAPSHOT` versions get no draft.
3. **Review and publish.** Under *Releases*, check the draft's notes and target, then
   *Publish release*. That creates the tag.
4. **Upload.** `publish.yml` checks the tag is on `main`, finds the module that declares that
   version, and builds and publishes that module only.

Publish libraries before the BOM: `publish.yml` refuses a `bom/vX.Y.Z` while any version in
`platform/sdk-bom/bom-lock.properties` is missing from GitHub Packages. To retry a failed upload,
run *Publish* by hand (`workflow_dispatch`) with the tag; versions already uploaded are skipped.
`./gradlew releaseCandidate` writes the tags a commit would draft to `build/release-candidates/`.
Publishing locally needs `GH_PACKAGES_USERNAME` / `GH_PACKAGES_TOKEN`.

The three workflows call the shared `java-library-*` workflows of `Codehunters-IO/ci-templates`
([ADR 0027](docs/adr/0027-release-workflows-from-ci-templates.md)); the BOM check is
`.github/scripts/verify-bom-release.sh`.

## Quality gates

- Spotless (`googleJavaFormat`) — `./gradlew spotlessCheck` / `spotlessApply`
- JaCoCo — LINE ≥ 90% and BRANCH ≥ 80% per module, enforced on `check`. The gates are the same for
  every module: `verifyCoverageGates` fails the build if `coverage.branch.minimum` is set.
- BOM — `verifyBomConstraints` fails the build when a released module is missing from `sdk-bom`, or
  a `-SNAPSHOT` or excluded one (`codehuntersBom.excludedModules`) is listed. `verifyBomLock` fails when a
  library version changes while the BOM version doesn't: bump `sdk-bom` and run
  `./gradlew :sdk-bom:updateBomLock`, which records the versions in
  `platform/sdk-bom/bom-lock.properties`.
- Pull requests also run the shared TruffleHog secret scan and commit lint (`commitlint.config.js`)
  from `Codehunters-IO/ci-templates@v2`. GitHub dependency review is not used: on a private
  repository it needs GitHub Advanced Security; the OWASP scan covers known CVEs.
- OWASP dependency check — `./gradlew dependencyCheckAnalyze` (aggregate report in
  `build/reports/dependency-check/`, fails on CVSS ≥ 7, suppressions in
  `config/dependency-check/suppressions.xml`). `security-scan.yml` runs it on demand (*Run workflow*):
  run it before a release. It needs the `NVD_API_KEY` secret; a run with no cached NVD database can
  take two hours.

See [CONTRIBUTING.md](CONTRIBUTING.md) for branches, commits and the definition of done.
