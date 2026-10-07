---
status: accepted
date: 2026-10-05
decision-makers: [andres]
---

# 0004. Use the codehunters.<sdk> property prefix with deprecated aliases

## Context and Problem Statement

Module properties use five unrelated schemes: `vitxo.*` (inherited from the previous library
family), `outbox.*`, `geo.validation.*`, `keycloak.*`, and none at all. Short generic prefixes such as
`outbox` and `keycloak` can collide with application or third-party keys.

## Considered Options

- Option A — `codehunters.<sdk>.*` everywhere, with old keys honoured for one major version.
- Option B — `codehunters.<sdk>.*` with a hard break.
- Option C — Keep the current prefixes.

## Decision Outcome

Chosen option: **Option A**, because it gives one namespace without forcing every service to change
its configuration on the day it upgrades.

### Consequences

- **Good** — one namespace, documented in configuration metadata.
- **Bad** — each module carries an alias `EnvironmentPostProcessor` until its next major.
- **Neutral** — old keys are copied to new ones only when the new key is absent, with one WARN.

### Confirmation

- Each module's auto-configuration test binds the same values from the old and the new keys.
- `META-INF/additional-spring-configuration-metadata.json` marks each old key deprecated with its
  replacement.

## More Information

- Mapping table: `docs/plans/2026-10-05-sdk-migration.md`, section "Prefijos de propiedades".
