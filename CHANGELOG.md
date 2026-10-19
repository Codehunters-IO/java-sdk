# Changelog

Each module keeps its own changelog; this file indexes them and records changes to the build itself.
Releases are tagged `sdk-<module>/vX.Y.Z` (`bom/vX.Y.Z` for the BOM) by the run that publishes them.

## Modules

| Module | Current | Changelog | Migration |
|---|---|---|---|
| `sdk-bom` | 7.2.0 | [below](#sdk-bom-720) | — |
| `sdk-user-security` | 6.0.0 | [CHANGELOG](security/sdk-user-security/CHANGELOG.md) | [4.0](security/sdk-user-security/MIGRATION-4.0.md) · [5.0](security/sdk-user-security/MIGRATION-5.0.md) · [6.0](security/sdk-user-security/MIGRATION-6.0.md) |
| `sdk-user-security-authz` | 6.0.0 | [CHANGELOG](security/sdk-user-security-authz/CHANGELOG.md) | [4.0](security/sdk-user-security-authz/MIGRATION-4.0.md) · [5.0](security/sdk-user-security-authz/MIGRATION-5.0.md) · [6.0](security/sdk-user-security-authz/MIGRATION-6.0.md) |
| `sdk-tracing` | 3.0.0 | [CHANGELOG](sdk-tracing/CHANGELOG.md) | [2.0](sdk-tracing/MIGRATION-2.0.md) · [3.0](sdk-tracing/MIGRATION-3.0.md) |
| `sdk-event-messaging-core` | 9.2.0 | [CHANGELOG](messaging/sdk-event-messaging-core/CHANGELOG.md) | [6.0](messaging/MIGRATION-6.0.md) · [7.0](messaging/MIGRATION-7.0.md) · [8.0](messaging/MIGRATION-8.0.md) · [9.0](messaging/MIGRATION-9.0.md) |
| `sdk-event-messaging-jdbc` | 9.2.0 | [CHANGELOG](messaging/sdk-event-messaging-jdbc/CHANGELOG.md) | [9.0](messaging/MIGRATION-9.0.md) |
| `sdk-event-messaging-rabbit` | 9.2.0 | [CHANGELOG](messaging/sdk-event-messaging-rabbit/CHANGELOG.md) | [9.0](messaging/MIGRATION-9.0.md) |
| `sdk-event-messaging-kafka` | 9.2.0 | [CHANGELOG](messaging/sdk-event-messaging-kafka/CHANGELOG.md) | [9.0](messaging/MIGRATION-9.0.md) |
| `sdk-file-share` | 5.0.0 | [CHANGELOG](sdk-file-share/CHANGELOG.md) | [2.0](sdk-file-share/MIGRATION-2.0.md) · [3.0](sdk-file-share/MIGRATION-3.0.md) · [4.0](sdk-file-share/MIGRATION-4.0.md) · [5.0](sdk-file-share/MIGRATION-5.0.md) |
| `sdk-audit-log` | 5.2.0 | [CHANGELOG](sdk-audit-log/CHANGELOG.md) | [3.0](sdk-audit-log/MIGRATION-3.0.md) · [4.0](sdk-audit-log/MIGRATION-4.0.md) · [5.0](sdk-audit-log/MIGRATION-5.0.md) |
| `sdk-measure-micrometer` | 4.0.0 | [CHANGELOG](sdk-measure-micrometer/CHANGELOG.md) | [3.0](sdk-measure-micrometer/MIGRATION-3.0.md) · [4.0](sdk-measure-micrometer/MIGRATION-4.0.md) |
| `sdk-parameter` | 4.0.0 | [CHANGELOG](sdk-parameter/CHANGELOG.md) | [2.0](sdk-parameter/MIGRATION-2.0.md) · [3.0](sdk-parameter/MIGRATION-3.0.md) · [4.0](sdk-parameter/MIGRATION-4.0.md) |
| `sdk-errors` | 1.0.0 | [CHANGELOG](sdk-errors/CHANGELOG.md) | — |
| `sdk-commons` | 4.0.0 | [CHANGELOG](sdk-commons/CHANGELOG.md) | [1.1](sdk-commons/MIGRATION-1.1.md) · [2.0](sdk-commons/MIGRATION-2.0.md) · [3.0](sdk-commons/MIGRATION-3.0.md) · [4.0](sdk-commons/MIGRATION-4.0.md) · [from commons-support](sdk-commons/MIGRATION-from-commons-support.md) |
| `sdk-web` | 4.0.0 | [CHANGELOG](sdk-web/CHANGELOG.md) | [1.0](sdk-web/MIGRATION-1.0.md) · [2.0](sdk-web/MIGRATION-2.0.md) · [3.0](sdk-web/MIGRATION-3.0.md) · [4.0](sdk-web/MIGRATION-4.0.md) |
| `sdk-barcode` | 3.0.0 | [CHANGELOG](sdk-barcode/CHANGELOG.md) | [2.0](sdk-barcode/MIGRATION-2.0.md) · [3.0](sdk-barcode/MIGRATION-3.0.md) · [from commons-support](sdk-barcode/MIGRATION-from-commons-support.md) |
| `sdk-jasper` | 2.1.0 | [CHANGELOG](sdk-jasper/CHANGELOG.md) | [2.0](sdk-jasper/MIGRATION-2.0.md) · [from commons-support](sdk-jasper/MIGRATION-from-commons-support.md) |
| `sdk-jpa` | 4.0.0 | [CHANGELOG](sdk-jpa/CHANGELOG.md) | [2.0](sdk-jpa/MIGRATION-2.0.md) · [3.0](sdk-jpa/MIGRATION-3.0.md) · [4.0](sdk-jpa/MIGRATION-4.0.md) · [from commons-support](sdk-jpa/MIGRATION-from-commons-support.md) |
| `sdk-geo-validation` | 2.0.0 | [CHANGELOG](sdk-geo-validation/CHANGELOG.md) | [2.0](sdk-geo-validation/MIGRATION-2.0.md) |

## sdk-bom 7.2.0

- `sdk-event-messaging-*` 9.2.0: a context that is stopped and started again dispatches again. 9.1.0
  stopped the dispatch use case and the commit-time trigger on stop and never resumed them.

## sdk-bom 7.1.0

Outbox operability (architecture review, 2026-10-13):

- `sdk-event-messaging-*` 9.1.0: backlog gauges (`outbox_pending_messages`,
  `outbox_oldest_pending_age_seconds`); a stopping context releases the unsent rest of a running
  batch after the current send, so shutdowns no longer leave claims and the dispatch lock held; a
  startup check for an outbox that is written to but never dispatched.

## sdk-bom 7.0.0

Closes insecure defaults that consumers inherited silently (architecture review, 2026-10-13):

- `sdk-user-security` 6.0.0: a plain `http` `codehunters.security.url` or `jwk-set-uri` fails
  startup unless `codehunters.security.allow-insecure-http=true`; the signing keys and client
  secrets travel there.
- `sdk-user-security-authz` 6.0.0: needs `sdk-user-security` 6.0.0. Its HTTP client, filter
  registration, customizer and failure counter can be replaced by the application.
- `sdk-file-share` 5.0.0: `presigned.allowed-hosts` is required unless
  `presigned.allow-any-host=true`, and presigned URLs must use port 443.
- `sdk-audit-log` 5.2.0: an application-defined `AuditAspect` replaces the SDK's instead of auditing
  every call twice.

## sdk-bom 6.1.0

- `sdk-audit-log` 5.1.0: `codehunters.audit.sink=outbox` writes audit events through the
  transactional outbox, synchronously in the caller's transaction, with PII-safe payloads and the
  entity id as message key; `jdbc` stays the default
  ([ADR 0029](docs/adr/0029-audit-events-through-the-outbox.md)).

## sdk-bom 6.0.0

Moves the error contract shared by the SDKs into its own module
([ADR 0028](docs/adr/0028-error-contract-in-its-own-module.md)):

- `sdk-errors` 1.0.0 (new): `io.codehunters.errors.ClientInputException`, moved from `sdk-commons`.
- `sdk-commons` 4.0.0: the `error` package is gone.
- `sdk-jpa` 4.0.0 and `sdk-barcode` 3.0.0: their exceptions extend the moved class, and they depend
  on `sdk-errors` instead of bringing all of `sdk-commons`.
- `sdk-web` 4.0.0: answers the moved class, through an `api` dependency on `sdk-errors` instead of
  an optional `sdk-commons`.

## sdk-bom 5.0.0

Shrinks the SDK onto the platform (architecture review, block 3). Folds in 4.0.0, which was never
published:

Developer experience and throughput (architecture review, block 4,
[ADR 0026](docs/adr/0026-developer-experience-defaults.md)), in the same unreleased majors:

- `sdk-event-messaging-*` 9.0.0: immutable records and a fluent `OutboxEvent` write API,
  `OutboxWriter` / `OutboxDispatchStore` instead of `OutboxRepositoryPort`, dispatch on commit,
  up to `claim.max-per-key` rows per key, requeue of `FAILED` rows, a private scheduler and clock,
  `Duration` timing properties and an outbox JSON mapper copied from Boot's builder.
- `sdk-user-security` 5.0.0: session introspection is opt-in (security-relevant default change) and
  cached in Caffeine with metrics.
- `sdk-web` 3.0.0: Caffeine user-agent cache with metrics, device beans in servlet applications
  only, opt-in `ForwardedClientIpFilter`.
- `sdk-jpa` 3.0.0: JPA auditing is opt-in.
- `sdk-audit-log` 5.0.0: read-only SpEL sandbox; JSON mapper from Boot's builder.
- `sdk-file-share` 4.0.0: Spring Security's OAuth2 interceptor, signed `upload` helper, in-memory
  test fake.
- Review fixes: outbox envelope names pinned against the application's Jackson setup, optional
  `claim.commit-grace`, graceful shutdown, `requeueFailed(topic, key)` and chunked requeue,
  startup failure on the 8.x `*-ms` keys; `ForwardedClientIpFilter` guarded against Spring's
  `ForwardedHeaderFilter`; startup warnings for unused introspection credentials and for auditable
  entities without JPA auditing; a dedicated file-share client manager
  (`fileShareAuthorizedClientManager`); re-entrant cache loads fail instead of deadlocking; audit
  rejects unknown `#variables` at startup.
- `sdk-parameter` 4.0.0 and `sdk-jasper` 2.1.0: Caffeine caches with metrics; parameter cache
  invalidation.

- `sdk-tracing` 3.0.0: built on Micrometer Tracing; keeps only the OpenTelemetry propagator for the
  legacy gateway headers and a `TraceContextPort` backed by the `Tracer`
  ([ADR 0022](docs/adr/0022-tracing-on-micrometer-tracing.md)). The servlet filter, task decorator
  and RabbitMQ instrumentation are removed in favour of Boot's. Legacy `x-trace-id` requests are
  sampled by trace-id ratio instead of always; the MDC fallback reports unsampled traces when it has
  no flags, and the trace id is empty without a tracer bridge.
- `sdk-event-messaging-*` 9.0.0: outbox rows store the writing request's `traceparent` (new column,
  `postgresql/outbox_message_add_traceparent.sql`) and both transports publish it
  ([ADR 0024](docs/adr/0024-outbox-carries-its-own-trace-context.md)). Values pass the shared
  `W3cTraceParent` check first; invalid ones are stored as `NULL` and never sent.
- `sdk-user-security-authz` 5.0.0: Keycloak's official policy enforcer inside the Spring Security
  chain. It fails closed: every authenticated request is enforced unless its path is in
  `skip-paths`; Keycloak only gets the token Spring Security validated, read from the
  authentication, never from the request; other authentications, ambiguous encoded paths and
  tokens outside the bearer charset, tokens that are not JWTs and permission tokens issued to
  another client answer 403; paths match as Spring MVC matches them; a slow first build answers 503
  after 5 s and a failed one during `retry-backoff`; startup fails when a public, excluded or
  skipped path overlaps an enforced catalogued path in either direction; failures are counted as
  `codehunters.authz.enforcer.failures`
  ([ADR 0023](docs/adr/0023-authz-on-keycloak-policy-enforcer.md)).
- `sdk-user-security` 5.0.0: bearer requests authenticate as `KeycloakAuthenticationToken`
  (`AbstractOAuth2TokenAuthenticationToken<Jwt>`), so the validated token survives credential
  erasure; the principal is still the `KeycloakPrincipal`. Only access tokens (`typ` = `Bearer`)
  are accepted by default (`codehunters.security.require-access-token-type`).
- `sdk-web` 3.0.0: i18n on Boot's message source and an `X-Locale` resolver with Boot's defaults
  that always answers a supported locale; 401/403 details follow the application's locale
  resolver; without an application bundle the errors part registers a message source with the SDK
  bundle, which now ships an explicit English file
  ([ADR 0025](docs/adr/0025-i18n-on-boot-message-source.md)).

## sdk-bom 4.0.0 (not published on its own)

PostgreSQL only, configuration under `codehunters.*` only, no aliases for older property prefixes
([ADR 0021](docs/adr/0021-postgresql-only-and-codehunters-properties.md)). Folds in 3.1.0, which
was never published:

- `sdk-event-messaging-*` 9.0.0: PostgreSQL only; the dialect selection and `claim.dialect` are
  gone; startup fails when the outbox data source is not PostgreSQL; the `outbox.*` aliases are
  removed. Tests that ran the outbox on H2 need one of the fixes in its MIGRATION-9.0.
- `sdk-web` 3.0.0: keys move to `codehunters.web.errors`, `codehunters.web.device` and
  `codehunters.web.i18n`; the old i18n aliases are removed. The configuration metadata flags the
  old `codehunters.*` keys as errors with their replacements.
- `sdk-audit-log` 5.0.0, `sdk-file-share` 4.0.0, `sdk-user-security` and `-authz` 5.0.0: the
  aliases for older property prefixes are removed; audit ships PostgreSQL DDL only.
- `sdk-geo-validation` 2.0.0: polygon and PostGIS adapters only; `geo.validation.enabled` is gone.
- `sdk-jpa` 3.0.0: PostgreSQL is the only supported database.
- `sdk-parameter` 4.0.0: ships PostgreSQL DDL; `ParameterModule` has no built-in product modules.
- `sdk-commons` 3.0.0 removes the TOTP legacy property aliases and no longer depends on `sdk-support`.
- `sdk-support` is removed and no longer managed by the BOM.
- Build: `verifyNoLegacyAliases`, part of `check`, fails a module that registers an
  `EnvironmentPostProcessor`.

## sdk-bom 3.1.0

Not published; its changes ship in 4.0.0.

Block 2 of the architecture review (silent failures and client-input errors), on top of 3.0.0,
which was never published on its own:

- `sdk-commons` 3.0.0 also adds `ClientInputException`: an `IllegalArgumentException` with a stable
  `code` whose message never echoes client values.
- `sdk-web` 2.1.0: a `ClientInputException`, direct or behind known wrappers
  (`InvalidDataAccessApiUsageException`, `CompletionException`, `ExecutionException`,
  `UndeclaredThrowableException`, `ServletException`), answers 400 with its code instead of 500;
  other wrappers keep the 500 and `@ResponseStatus` exceptions their status. The SDK's own codes
  get English and Spanish details by default. One raised while Spring binds the request (Jackson,
  converters) still gets the framework's generic 400 without the code.
- `sdk-jpa` 2.1.0: a sort outside the whitelist throws `InvalidSortException` (`INVALID_SORT`) and
  `InvalidSearchException` carries `INVALID_SEARCH`; a conversion failure no longer names the
  attribute's Java type. `sdk-jpa` and `sdk-barcode` now bring `sdk-commons`.
- `sdk-barcode` 2.1.0: rejected images and render requests throw `BarcodeRejectedException`
  (`BARCODE_IMAGE_REJECTED`, `BARCODE_CONTENT_REJECTED`).
- `sdk-audit-log` 4.1.0: `@Auditable` methods with no publisher or no `aspectjweaver`, or with an
  unparseable expression, are reported at startup, failing it only with the new
  `codehunters.audit.startup-check.fail-fast=true`; expression failures warn once per overload; the
  servlet actor defaults to the Spring Security user while a request is bound; new
  `codehunters.audit.enabled`.

## sdk-bom 3.0.0

Not published on its own; its changes ship in 4.0.0. Fixes from block 1 of the architecture review (security and personal data):

- `sdk-commons` 3.0.0: TOTP defaults are HMAC-SHA1, 6 digits and 30 s, which authenticator apps
  expect; the TOTP auto-configuration is opt-in (`codehunters.totp.enabled`); `verifyStep` and
  `verifyAfter` return the matched step so callers can reject replayed codes; `provisioningUri`
  builds the `otpauth://` key URI.
- `sdk-user-security` 4.1.0: the authentication name is the user id, no longer the principal's
  `toString()` with IP and location; gateway `x-geo-*` and `User-Agent` values move to an
  unverified `GatewayContext`; the gateway-header 401 goes through the application's entry point.
- `sdk-jasper` 2.1.0: location-typed parameters are rejected at any nesting depth; the render
  timer carries a `template` tag.
- `sdk-support` 1.0.1: a legacy list or map property is copied from one source only, never merged
  across profiles.

## sdk-bom 2.0.0

- Every module is packaged by capability instead of by architectural layer
  ([ADR 0020](docs/adr/0020-package-by-capability.md)). Only imports change; properties and
  behaviour do not. New majors: `sdk-tracing` 2.0.0, `sdk-event-messaging-*` 8.0.0, `sdk-web` 2.0.0,
  `sdk-commons` 2.0.0, `sdk-audit-log` 4.0.0, `sdk-user-security` and `-authz` 4.0.0, `sdk-jasper`
  2.0.0, `sdk-barcode` 2.0.0, `sdk-measure-micrometer` 4.0.0, `sdk-file-share` 3.0.0,
  `sdk-parameter` 3.0.0, `sdk-jpa` 2.0.0. `sdk-geo-validation` stays 1.1.0.
- Build: `verifyPackageLayout`, part of `check`, enforces the layout in every library module.

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

- Monorepo: the ten legacy SDK libraries move to the `io.codehunters` group under
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
