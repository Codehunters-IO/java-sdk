---
status: accepted
date: 2026-10-05
decision-makers: [andres]
---

# 0005. Rename sdk-utils to sdk-totp

## Context and Problem Statement

`sdk-utils` contains only TOTP generation and verification (`io.codehunters.totp`). A module named
"utils" invites unrelated helpers to accumulate in it and hides what a service is depending on.

## Considered Options

- Option A — Rename the artifact to `sdk-totp`.
- Option B — Keep `sdk-utils` and define what else may go in it.

## Decision Outcome

Chosen option: **Option A**, because the artifact name then states its single responsibility.

### Consequences

- **Good** — the dependency says what it provides.
- **Bad** — consumers change the coordinate.
- **Neutral** — the package `io.codehunters.totp` does not change. Released as 2.0.0 together with
  the locale and time-step fixes.

### Confirmation

- `sdk-totp` publishes the capability `io.codehunters:sdk-utils`, so both cannot be resolved together.

## More Information

- Migration plan: `docs/plans/2026-10-05-sdk-migration.md`, phase 10.
