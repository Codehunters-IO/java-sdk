---
status: accepted
date: 2026-10-05
decision-makers: [andres]
---

# 0008. Keep @Measured and its own aspect

## Context and Problem Statement

`sdk-measure-micrometer` provides `@Measured`, an aspect that records a timer, error and count meters
with SpEL-derived tags. Micrometer ships `@Timed`, `@Counted` and `@MeterTag` with similar
capabilities. The module has defects (it is never registered in real applications, SpEL failures
break the business call, tag keys vary), so it must change either way.

## Considered Options

- Option A — Keep `@Measured` and its aspect, and fix it.
- Option B — Replace it with Micrometer's annotations.
- Option C — Keep `@Measured` as a thin facade over Micrometer's aspects.

## Decision Outcome

Chosen option: **Option A**, because services already annotate with `@Measured` and its
`countCondition` and expression semantics have no direct Micrometer equivalent.

### Consequences

- **Good** — no annotation migration in consuming services.
- **Bad** — the SDK keeps maintaining an aspect that overlaps with Micrometer.
- **Neutral** — meter names move to Micrometer conventions in 3.0.0, which is a breaking change for
  dashboards.

### Confirmation

- An auto-configuration test with Boot's real metrics auto-configuration proves the aspect registers.
- Branch coverage returns to the 0.80 default in 3.0.0.

## More Information

- Migration plan: `docs/plans/2026-10-05-sdk-migration.md`, phase 7.
