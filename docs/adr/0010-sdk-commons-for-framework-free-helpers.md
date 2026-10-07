---
status: accepted
date: 2026-10-07
decision-makers: [andres]
---

# 0010. Put framework-free helpers in sdk-commons

## Context and Problem Statement

The old `codehuntersio-java-commons-support` repository holds loose utilities (masking, text
simplification, password generation, date and file helpers) that services still want. They were
written for Java 11 with Lombok and no tests, and several have bugs. Where should the ones worth
keeping live, and under which name?

The natural name, `sdk-utils`, is taken: `sdk-totp` 2.0 publishes the Gradle capability
`io.codehunters:sdk-utils` so that Gradle rejects a graph holding both the old `sdk-utils` and
`sdk-totp` ([ADR 0005](0005-rename-sdk-utils-to-sdk-totp.md)).

## Considered Options

- Option A — A new module `sdk-commons`, pure JDK.
- Option B — Reuse `sdk-utils` as 3.0.0 for the helpers and drop the capability from `sdk-totp`.
- Option C — `sdk-util` (singular).
- Option D — Copy the helpers into each service.

## Decision Outcome

Chosen option: **Option A**, because it keeps the `sdk-utils` → `sdk-totp` guard intact and gives
the helpers a name that does not suggest the old TOTP artifact.

- `sdk-commons` depends only on the JDK: no Spring, no Lombok, no Jackson, no auto-configuration.
  An ArchUnit test fails the build if that changes.
- Only helpers that the JDK does not already cover are kept, each rewritten with tests. The others
  are listed with their JDK replacement in `sdk-commons/MIGRATION-from-commons-support.md`.
- Exceptions never repeat their input, so the helpers are safe to use on sensitive values.
- `sdk-commons` is part of `sdk-bom` 1.0.0, which had not been published yet.

### Consequences

- Good: no capability clash; services get tested replacements for helpers they copy around today.
- Good: no framework coupling, so the module can be used from any layer, domain included.
- Bad: services moving from `commons-util` must change imports and some call sites; the migration
  table covers each one.
- Neutral: Option B was rejected because a service still on `sdk-utils` 1.x that upgrades would
  silently lose TOTP. Option C was rejected as too easy to confuse with `sdk-utils`.
