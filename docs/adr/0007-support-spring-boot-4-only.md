---
status: accepted
date: 2026-10-05
decision-makers: [andres]
---

# 0007. Support Spring Boot 4 only

## Context and Problem Statement

The modules are built against Spring Boot 4.0 and Jackson 3. Several auto-configurations order
themselves with `afterName`/`before` on Boot 4 class names (for example
`org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`), which do not exist in
Boot 3. On Boot 3 those constraints are silently ignored and beans can be registered too early or
not at all.

## Considered Options

- Option A — Support Boot 4 only, and say so.
- Option B — Support Boot 3 and 4 by listing both class names and testing on both.

## Decision Outcome

Chosen option: **Option A**, because every consuming service is on Boot 4 and a second test matrix
would cost more than it protects.

### Consequences

- **Good** — one set of class names, one test matrix.
- **Bad** — a Boot 3 service cannot adopt the SDK without upgrading.
- **Neutral** — the README states the requirement.

### Confirmation

- The build compiles and tests only against the Boot 4 BOM (`spring.boot.version`).
