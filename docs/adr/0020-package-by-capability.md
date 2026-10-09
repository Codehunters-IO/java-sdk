# 0020 — Package the SDKs by capability, not by architectural layer

- Status: accepted
- Date: 2026-10-09

## Context

The SDK modules grew three package styles. `sdk-event-messaging-*`, `sdk-file-share` and
`sdk-parameter` copied the hexagonal layout of a service (`application/port`, `domain/model`,
`infrastructure/drivenadapters/jdbc`). Most other modules were packaged by topic, several with one
flat package of 12–24 classes that mixed public API, aspects, auto-configuration and helpers.
`io.codehunters.messaging.config` was shipped by all four messaging jars (a split package), and the
packages of `sdk-web` (`weberrors`, `device`, `i18n`) and of TOTP in `sdk-commons` (`totp`) did not
match their artifact.

A library is not a service: it has no use cases to orchestrate and no inbound adapters of its own.
What a consumer needs from its packages is to see at a glance which types are API, where the
auto-configuration lives and what may change without notice.

No service consumes these artifacts yet; the previous versions are published but unused.

## Decision

Every library module follows one layout:

- `io.codehunters.<module>`, the root package, holds the public API: annotations, value types,
  interfaces meant to be injected or implemented, exceptions and the main entry classes.
- Sub-packages are named by capability (`messaging.jdbc`, `audit.persistence`, `web.errors.servlet`,
  `web.device.ip`), never by layer: `application`, `domain`, `infrastructure`, `usecase`, `port(s)`,
  `drivenadapters`, `entrypoint` and `adapter(s)` are not used as segments.
- `config` holds auto-configuration, `@ConfigurationProperties`, legacy-alias post-processors and
  conditions. The API package does not import from it.
- `internal` holds implementation that is not API; its `package-info` says so.
- A package belongs to one artifact. Each module declares the root it owns with
  `codehuntersPackages { root = ... }` (for example `io.codehunters.messaging.jdbc`), and no other
  module may place classes under it.
- Every package has a `package-info.java`.

`verifyPackageLayout`, added to `check` by the `codehunters.java-library` convention, reads the
sources and fails the build when:

- a package segment is one of the layer names above;
- a package has no `package-info.java`;
- a package lies outside the module's declared root, under a more specific root another module
  declares, or the module's root is also declared by another module;
- a class in the root (API) package imports, statically imports or names by its fully qualified name
  a type under a `config` package of the module (comments are ignored; string literals are not);
- a package with an `internal` segment has a `package-info.java` that does not say "Not API".

The rest is a convention checked in review: that `config` holds only wiring, properties, aliases
and conditions, that only API lives in the root package, and that implementation consumers should
not use goes in `internal` or says "not API" in its Javadoc.

Packages moved outright, without deprecated copies in the old packages: no service would use them,
annotations and records cannot be aliased, and each module with moved packages gets a new major
with a `MIGRATION-<major>.0.md` table from old to new names. Property names, legacy aliases and
behaviour do not change; alias deadlines that fell on the new major move one major further.

## Consequences

- Upgrading is an import change: an IDE's "organise imports" with the migration table covers it.
- Exclusions and `afterName`/`beforeName` strings that name an auto-configuration class by its old
  name must be updated by hand.
- Classes split into feature packages sometimes had to become public to be shared; those that are
  not API live in `internal` or say "not API" in their Javadoc. ZXing types no longer appear in the
  barcode API enums.
- A new module must declare its package root before `check` passes.
- `sdk-geo-validation` already followed the layout and kept its version.
