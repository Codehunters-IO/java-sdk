---
status: accepted
date: 2026-10-05
decision-makers: [andres]
---

# 0006. Publish an sdk-bom and stop exporting the Spring Boot BOM

## Context and Problem Statement

Each module declared `implementation(platform(spring-boot-dependencies))`, which was published as an
imported BOM in its POM and as platform constraints in its Gradle metadata. Every consumer therefore
inherited Spring Boot 4.0.2 constraints from each SDK module, and had no single place to align the
SDK's own versions.

## Considered Options

- Option A — Keep the Boot BOM internal to the build, publish resolved versions, and add an
  `sdk-bom` `java-platform` that lists the SDK modules.
- Option B — Keep exporting the Boot BOM and add `sdk-bom`.
- Option C — Change nothing; document recommended versions.

## Decision Outcome

Chosen option: **Option A**, because a library must not dictate the application's Spring Boot
patch level, and services need one import to keep the SDK modules consistent.

### Consequences

- **Good** — the application's own Boot BOM decides framework versions.
- **Good** — one `platform("io.codehunters:sdk-bom:x")` aligns all SDK modules.
- **Neutral** — published dependencies carry the versions resolved at build time; the consumer's BOM
  still wins in its own resolution.

### Confirmation

- The generated POMs contain no `dependencyManagement` import of `spring-boot-dependencies`
  (checked when the change was introduced).
- `sdk-bom` is excluded from library plugins and published from `components["javaPlatform"]`.

## More Information

- Build change: commit `build: apply java-library and stop exporting the Boot BOM`.
