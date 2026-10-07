---
status: accepted
date: 2026-10-07
decision-makers: [andres]
---

# 0013. JPA building blocks: UUID v7 entities, Spring Data auditing and whitelisted queries

## Context and Problem Statement

Services repeat the same JPA plumbing: a base entity with an id and audit columns, a page shape for
APIs, JSON columns, and dynamic search from request parameters. The old `commons-repository` and
friends offered this with identity `Long` ids, `Date` audit fields that nothing populated, converters
whose exceptions printed the data, and a search builder that accepted any attribute name from the
client and had broken `BETWEEN` and unescaped `LIKE`. What should a shared JPA module provide, and
how far should it trust request input?

## Decision Drivers

- Ids that can be generated without a database round trip and index well.
- Audit columns filled the same way in every service, from a testable clock and the signed-in user.
- Client input never names a column the service did not choose, nor reaches a message or log.
- Spring Data and Hibernate do the work; the module only configures and constrains them.
- Persistence types stay in adapters (hexagonal layering).

## Considered Options

- Option A — `BaseEntity` with a UUID v7 id and `@Version`, `AuditableEntity` on Spring Data
  auditing enabled by the SDK, and sort and search only through explicit whitelists.
- Option B — Keep identity `Long` ids and leave auditing and search to each service.
- Option C — Expose Querydsl or a generic query language for search.

## Decision Outcome

Chosen option: **Option A**.

- **Ids.** Hibernate 7's `@UuidGenerator(style = VERSION_7)` generates time-ordered UUIDs on persist:
  no sequence or identity round trip, inserts stay mostly append-only in B-tree indexes, and ids are
  safe to expose. `BaseEntity` is a Spring Data `Persistable`, new while it has no id, so
  repositories persist new entities rather than merge a copy; its accessors are record style because
  Spring reads `Optional`-returning `getX()` methods as bean properties. Equality is by class and id,
  final, and reads proxies through their lazy initializer without loading them; `hashCode()` is per
  class so hash collections survive the transition from new to persisted.
- **Auditing.** The SDK enables Spring Data JPA auditing with a `Clock`-based `DateTimeProvider`
  (microsecond precision, what the databases keep) and, with Spring Security, an `AuditorAware`
  that returns the authenticated name. It steps aside for an application's own
  `@EnableJpaAuditing`, provider beans, or a property.
- **Whitelists.** `SortWhitelist` and `SpecificationBuilder` map API names to entity property paths
  and reject everything else. Search values are converted to the attribute type; `LIKE` escapes its
  wildcards; to-one associations are left-joined once per query, and a path through a collection
  is a correlated `EXISTS` subquery per condition, so the outer query is never `distinct` and stays
  sortable by any whitelisted property; criteria, `IN` lists and `LIKE` values are capped. Errors
  never repeat the client's input.

### Consequences

- Good, because sorting or filtering by an unexpected column is impossible by construction.
- Good, because audit data is consistent and testable across services.
- Bad, because tables with numeric identity ids cannot adopt `BaseEntity` without a data migration.
- Bad, because a per-class `hashCode()` makes large hash sets of one entity class slow.
- Neutral, because conversion errors surface while the query runs, wrapped by Spring in
  `InvalidDataAccessApiUsageException`; handlers look for `InvalidSearchException` in the cause
  chain.

## Pros and Cons of the Options

### Option B — Identity ids, per-service plumbing

- Good, because existing schemas fit.
- Bad, because every service rebuilds auditing and search, and the search security bugs come back.

### Option C — Querydsl or a query language

- Good, because it is more expressive.
- Bad, because it widens what clients can ask for and needs code generation; the whitelist would
  have to be rebuilt on top anyway.
