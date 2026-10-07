---
status: accepted
date: 2026-10-05
decision-makers: [andres]
---

# 0003. Retire sdk-geo-validation from the SDK

## Context and Problem Statement

`sdk-geo-validation` exposes `RaffleGeoValidationPort` and calls the SQL Server procedure
`dbo.sp_geo_can_purchase_raffle`. It is business logic of one service and one database vendor, not
shared infrastructure, yet it registers itself in any application that has a `DataSource`.

## Considered Options

- Option A — Move the code into the raffle service and remove the module.
- Option B — Generalise it (vendor-neutral port, configurable procedure) and keep it.

## Decision Outcome

Chosen option: **Option A**, because no second service needs it and generalising would design an
abstraction for a single caller.

### Consequences

- **Good** — the SDK stops shipping a raffle-specific, SQL Server-only bean.
- **Bad** — the raffle service owns the code and its tests from now on.
- **Neutral** — version 0.2.0 stays available in the package registry.

### Confirmation

- The module is removed from `settings.gradle.kts` and `sdk-bom` only after the raffle service has
  adopted the code. Before handing it over, the NaN coordinate check and `SET NOCOUNT ON` are fixed
  so the defects don't move with it.

## More Information

- Migration plan: `docs/plans/2026-10-05-sdk-migration.md`, phase 10.
