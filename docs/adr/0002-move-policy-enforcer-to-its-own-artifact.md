---
status: accepted
date: 2026-10-05
decision-makers: [andres]
---

# 0002. Move the Keycloak policy enforcer to sdk-user-security-authz

## Context and Problem Statement

`sdk-user-security` contains the request authentication filter that every service uses and the
optional Keycloak policy enforcer (PEP). The PEP needs `keycloak-authz-client`, which is declared
`implementation` and brings Jackson 2 and Apache HttpClient 4 to every consumer, including the ones
that never enable it.

## Considered Options

- Option A — Separate artifact `sdk-user-security-authz` that depends on `sdk-user-security`.
- Option B — Keep one artifact and make `keycloak-authz-client` `compileOnly`.

## Decision Outcome

Chosen option: **Option A**, because a consumer that wants the PEP declares it, and nobody else
receives its transitive dependencies or its auto-configuration.

### Consequences

- **Good** — the base artifact no longer carries the Keycloak authz client.
- **Bad** — PEP users add one coordinate.
- **Neutral** — released with the 3.0.0 trust-model changes; the package moves from
  `io.codehunters.sdk.security` to `io.codehunters.security` in the same major.

### Confirmation

- `-authz` publishes the capability of the 2.x `sdk-user-security`, so mixing 2.x and 3.x fails
  resolution.

## More Information

- Migration plan: `docs/plans/2026-10-05-sdk-migration.md`, phase 2.
