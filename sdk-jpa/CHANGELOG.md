# Changelog — sdk-jpa

## 1.0.0

First release ([ADR 0013](../docs/adr/0013-jpa-building-blocks.md)).

- `BaseEntity`: UUID version 7 id generated on persist, optimistic-locking `version`, equality by
  class and id that reads Hibernate proxies without initializing them, one hash code per class.
- `AuditableEntity`: `created_at`, `created_by`, `updated_at`, `updated_by` through Spring Data JPA
  auditing.
- `JpaAuditingAutoConfiguration`: enables auditing with the application's `Clock` (cut to
  microseconds) and, with Spring Security, the signed-in user's name. Steps aside for the
  application's own `@EnableJpaAuditing`, `DateTimeProvider` or `AuditorAware`, and with
  `codehunters.jpa.auditing.enabled=false`.
- `PageResponse<T>`: stable page record built from a Spring Data `Page`, with an element mapper and
  the sort orders.
- `SortWhitelist`: API sort names mapped to entity property paths; unknown or repeated names are
  rejected without repeating them.
- `JsonAttributeConverter<T>`, `JsonListConverter<E>`, `JsonSetConverter<E>`,
  `JsonMapConverter<V>` and the ready-made `StringListConverter`, `StringSetConverter`,
  `StringMapConverter`: values stored as JSON text with Jackson 3; reads are unmodifiable;
  `JsonColumnException` never carries the column content.
- `SpecificationBuilder<T>`, `SearchCriteria`, `SearchOperation`, `InvalidSearchException`:
  whitelisted search over entity attributes, left joins shared per query, values converted to the
  attribute type, `LIKE` with `%` and `_` escaped, inclusive `BETWEEN`, capped `IN` lists and
  criteria counts.
- Replaces `commons-repository`, `commons-jpa-mapper-support` (pagination), and
  `commons-jpa-converter-support` from `codehuntersio-java-commons-support`, and the pagination DTOs
  of `commons-dto` and `commons-web`; see
  [MIGRATION-from-commons-support.md](MIGRATION-from-commons-support.md).
