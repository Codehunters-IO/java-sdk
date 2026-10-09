---
status: superseded by 0015
date: 2026-10-05
decision-makers: [andres]
---

# 0003. Retire sdk-geo-validation from the SDK

## Context and Problem Statement

`sdk-geo-validation` exposes a port named after one service's domain and calls that service's SQL
Server stored procedure. It is business logic of one service and one database vendor, not
shared infrastructure, yet it registers itself in any application that has a `DataSource`.

## Considered Options

- Option A — Move the code into the service that uses it and remove the module.
- Option B — Generalise it (vendor-neutral port, configurable procedure) and keep it.

## Decision Outcome

Chosen option: **Option A**, because no second service needs it and generalising would design an
abstraction for a single caller.

### Consequences

- **Good** — the SDK stops shipping a single-service, SQL Server-only bean.
- **Bad** — that service owns the code and its tests from now on.
- **Neutral** — version 0.2.0 stays available in the package registry.

### Confirmation

- The module is removed from `settings.gradle.kts` and `sdk-bom` only after that service has
  adopted the code. Before handing it over, the NaN coordinate check and `SET NOCOUNT ON` are fixed
  so the defects don't move with it.

## More Information

- Migration plan: `docs/plans/2026-10-05-sdk-migration.md`, phase 10.
