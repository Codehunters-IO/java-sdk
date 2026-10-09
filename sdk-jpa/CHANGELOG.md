# Changelog — sdk-jpa

## 2.1.0

### Added
- `InvalidSortException` (code `INVALID_SORT`): `SortWhitelist.apply` throws it for a property
  outside the whitelist or the same one twice. It extends `sdk-commons`' `ClientInputException`, so
  it is still an `IllegalArgumentException`, as before.
- `InvalidSearchException` is now a `ClientInputException` (code `INVALID_SEARCH`), still an
  `IllegalArgumentException`. `sdk-web` 2.1.0 answers both with 400 and the code, also when Spring
  wraps the search exception in `InvalidDataAccessApiUsageException` at query time.
- `sdk-commons` 3.0.0 is an `api` dependency.

## 2.0.0

### Breaking
- `BaseEntity` and `AuditableEntity` move from `io.codehunters.jpa.domain` to
  `io.codehunters.jpa.entity`. Only imports change; columns, properties and beans are unchanged.
  See [MIGRATION-2.0.md](MIGRATION-2.0.md).

## 1.0.0

First release ([ADR 0013](../docs/adr/0013-jpa-building-blocks.md)).

- `BaseEntity`: UUID version 7 id generated on persist, optimistic-locking `version`, a Spring Data
  `Persistable` so `save` persists new entities instead of merging a copy, record-style `id()` and
  `version()`, and final equality by class and id that reads Hibernate proxies, detached ones
  included, without initializing them; one hash code per class.
- `AuditableEntity`: `created_at`, `created_by`, `updated_at`, `updated_by` through Spring Data JPA
  auditing, read with `createdAt()`, `createdBy()`, `updatedAt()`, `updatedBy()`.
- `JpaAuditingAutoConfiguration`: enables auditing with the application's `Clock` (cut to
  microseconds) and, with Spring Security, the signed-in user's name. Steps aside for the
  application's own `@EnableJpaAuditing`, `DateTimeProvider` or `AuditorAware`, and with
  `codehunters.jpa.auditing.enabled=false`.
- `PageResponse<T>`: stable page record built from a Spring Data `Page`, with an element mapper and
  the sort orders, reported under the API names when built with the `SortWhitelist`.
- `SortWhitelist`: API sort names mapped to entity property paths and back (`toApi`); unknown or
  repeated names are rejected without repeating them.
- `JsonAttributeConverter<T>`, `JsonListConverter<E>`, `JsonSetConverter<E>`,
  `JsonMapConverter<V>` and the ready-made `StringListConverter`, `StringSetConverter`,
  `StringMapConverter`: values stored as JSON text with Jackson 3; reads are unmodifiable;
  `JsonColumnException` never carries the column content. Values need value-based `equals`.
- `SpecificationBuilder<T>`, `SearchCriteria`, `SearchOperation`, `InvalidSearchException`:
  whitelisted search over entity attributes, left joins shared per query for to-one associations,
  a correlated `EXISTS` subquery per condition for collections (no `distinct`), values converted to
  the attribute type, `LIKE` with `%` and `_` escaped and capped at 256 characters, inclusive
  `BETWEEN`, no ordered comparison on enums, capped `IN` lists and criteria counts.
- Replaces `commons-repository`, `commons-jpa-mapper-support` (pagination), and
  `commons-jpa-converter-support` from `codehuntersio-java-commons-support`, and the pagination DTOs
  of `commons-dto` and `commons-web`; see
  [MIGRATION-from-commons-support.md](MIGRATION-from-commons-support.md).
