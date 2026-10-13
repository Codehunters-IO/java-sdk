# 0028 — The error contract lives in its own module

- Status: accepted
- Date: 2026-10-12

## Context

A coupling and cohesion review of the SDKs found one contract crossing module boundaries:
`ClientInputException`, which `sdk-jpa` and `sdk-barcode` extend and `sdk-web` answers with 400. It
lived in `sdk-commons`, next to seven unrelated packages (masking, text, passwords, relative time,
file names, streams, TOTP) that do not import one another.

Because their public exceptions extend it, `sdk-jpa` and `sdk-barcode` declared `sdk-commons` as an
`api` dependency, so every consumer of either received the whole toolbox, TOTP included. A change
to TOTP that needed a major release of `sdk-commons` reached those consumers as a new transitive
major they had not asked for. `sdk-web` read the contract optionally, through a class-presence
check and a test that hid `sdk-commons` from an isolated class loader, so whether a 400 was
answered depended on what else the application had on its class path.

## Decision

`ClientInputException` moves, unchanged apart from its package, to a new module:
`io.codehunters:sdk-errors`, package `io.codehunters.errors`, depending on the JDK only.

- `sdk-jpa` and `sdk-barcode` depend on `sdk-errors` with `api`, and no longer on `sdk-commons`.
- `sdk-web` depends on `sdk-errors` with `api`: a one-class contract with no dependencies costs less
  than the optional loading it replaces, and the 400 mapping no longer depends on the class path.
- `sdk-commons` keeps its helpers and loses the `error` package.

Versions: `sdk-errors` 1.0.0, `sdk-commons` 4.0.0, `sdk-jpa` 4.0.0, `sdk-barcode` 3.0.0,
`sdk-web` 4.0.0, `sdk-bom` 6.0.0.

## Consequences

- Each consumer of `sdk-jpa` or `sdk-barcode` stops receiving `sdk-commons`. One that used its
  helpers only through that transitive dependency now declares `sdk-commons` itself.
- Code that names the class changes one import: `io.codehunters.commons.error.ClientInputException`
  becomes `io.codehunters.errors.ClientInputException`. An exception of the old type, thrown by an
  application still on `sdk-commons` 3.x next to `sdk-web` 4.0.0, is no longer answered with 400;
  the BOM keeps the versions aligned.
- `sdk-commons` is still a set of unrelated helpers. Splitting it further was considered and left
  out: with the shared contract gone, none of its packages is used by another SDK, so the cost that
  motivated this change does not apply to the rest.

## Alternatives considered

- **Keep the class in `sdk-commons` and make it deprecated-extend a new one.** Keeps old `catch`
  blocks compiling for a release, but `sdk-jpa` and `sdk-barcode` would still drag `sdk-commons`
  until the next major, which is the problem being fixed.
- **Split `sdk-web` into errors, device and i18n.** Its three parts share nothing but the artifact,
  but they were merged on purpose (ADR 0016) and none of them leaks into another SDK's API.
