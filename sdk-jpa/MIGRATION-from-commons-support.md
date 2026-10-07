# Migrating from the commons-support JPA modules

`commons-repository`, `commons-jpa-mapper-support`, `commons-jpa-converter-support` and the
pagination DTOs of `commons-dto` and `commons-web` (`io.codehunters.commons`, 5.1.0, repository
`codehuntersio-java-commons-support`) are replaced by `io.codehunters:sdk-jpa` 1.0.0. Every class
was rewritten; none is source compatible.

## Entities

| commons-support | sdk-jpa |
|---|---|
| `JpaEntity<I extends Number>` (`IDENTITY` id, `Persistable`) | `BaseEntity`: UUID v7 id, `@Version`, `Persistable`, proxy-safe equality; `id()` instead of `getId()` |
| `AuditableJpaEntity` (`java.util.Date`; `created_by`, `created_date`, `modified_by`, `last_modified_date`) | `AuditableEntity` (`Instant`; `created_by`, `created_at`, `updated_by`, `updated_at`) |
| `UniqueIdJpaEntity` (`unique_Id varchar(80)`) | not migrated: the UUID id is the unique id; keep your own column if you need another |
| `GeolocationJpaEntity` (`latitude`, `longitude`) | not migrated: coordinates are the application's model |

- **Ids are not drop-in.** Tables with numeric identity ids cannot switch to `BaseEntity` without a
  data migration. Keep the old mapping in those adapters, or migrate with expand/contract: add the
  `uuid` column, backfill, move the foreign keys, then drop the old key.
- **Audit columns were renamed.** Keep the old names with `@AttributeOverride`, or rename the
  columns in a migration. `Date` fields become `Instant`.
- **Auditing was never enabled by the old module.** Applications had to add `@EnableJpaAuditing` and
  their own `AuditorAware`; the SDK now does it (see the README). Remove the application's
  `@EnableJpaAuditing` to use the SDK's clock and author, or keep it and the SDK steps aside.
- `JpaConstraintViolationException` and `JpaRepositoryException` are not migrated: use Spring's
  `DataAccessException` hierarchy (`DataIntegrityViolationException`, ...).

## Search

| commons-support | sdk-jpa |
|---|---|
| `GenericSpecificationsBuilder<T>` | `SpecificationBuilder<T>`, built once per entity with a whitelist of attributes |
| `SearchCriteria` (mutable, `isOr` per criterion) | `SearchCriteria` record; combine with `allOf`/`anyOf`, then `Specification.and`/`or` |
| `EQUALITY`, `NEGATION`, `LIKE`, `BETWEEN`, `IN` | `EQUAL`, `NOT_EQUAL`, `LIKE`, `BETWEEN`, `IN` |
| `GREATER_THAN`, `LESS_THAN` (declared, but they threw `UnsupportedOperationException`) | `GT`, `LT`, plus `GTE`, `LTE`, `IS_NULL`, `IS_NOT_NULL` |
| `STARTS_WITH` (declared, but it threw `UnsupportedOperationException`) | not migrated; use `LIKE` (contains) |

Fixed on the way:

- **Any attribute could be searched.** The old builder passed client input straight to
  `root.get(...)`. Only whitelisted names are searchable now.
- **`BETWEEN` compared the value with itself.** It took the first value for both ends, as text. It
  now takes two values, converted to the attribute's type.
- **`LIKE` treated `%` and `_` in the input as wildcards.** They are escaped now, and the match
  ignores case on both sides.
- **Values were compared as given.** They are converted to the attribute's type.
- **Nested paths were not supported.** `customer.name` is now a left join, shared per query, so rows
  without a customer still match an `OR` on another attribute. A path through a collection is an
  `EXISTS` subquery per condition.

## Pagination

| commons-support | sdk-jpa |
|---|---|
| `PaginationQueryDTO`, `PaginationRequest` (`page`, `size`, `sort` map, `query`) | Spring Data's `Pageable` bound from `page`, `size`, `sort=prop,dir`, passed through `SortWhitelist.apply(...)` |
| `JpaPaginationMapper.toPageRequest(...)` (any sort property; size capped at 1000) | `SortWhitelist.apply(pageable)`; size capped by `spring.data.web.pageable.max-page-size` |
| `JpaPaginationMapper.replaceQuery(...)` (`%query%`) | `SearchCriteria.of(attribute, LIKE, query)`, escaped |
| `JpaPaginationMapper.toPaginationResult(page)`, `PaginationResultDTO`, `PaginationResponse` | `PageResponse.from(page, mapper)` |

The JSON shape changes for clients:

| Old field | New field |
|---|---|
| `data` | `content` |
| `rows` | `totalElements` |
| `page` | `page` (still zero based) |
| `size` | `size` |
| — | `totalPages`, `sort` |

`JpaEntityMapper`, `DTOMapper` and the mapping annotations (`@IgnoreId`, `@IgnoreAuditable`, ...) are
not migrated: write MapStruct mappers in each service. `DTO`, `AuditableDTO` and `GeolocationDTO`
are not migrated either; response types belong to each API.

## Converters

| commons-support | sdk-jpa |
|---|---|
| `ListStringConverter` | `StringListConverter` |
| `SetStringConverter` | `StringSetConverter` |
| `MapStringKeyWithStringValueConverter` | `StringMapConverter` |
| `MapStringKeyWithObjectValueConverter` | a subclass of `JsonMapConverter<Object>` |
| `MapStringKeyWithListStringValueConverter` | a subclass of `JsonAttributeConverter<Map<String, List<String>>>` |
| `DomainAttributeConverter` (a new `ObjectMapper` per converter) | `JsonAttributeConverter<T>` with one shared Jackson 3 `JsonMapper`, or the application's |
| `DomainAttributeConverterException` | `JsonColumnException` |

- **Error messages carried the data.** The old exceptions printed the whole value or column, which
  ended up in logs. `JsonColumnException` names only the type and the parse position.
- **Reads are unmodifiable.** The old converters returned mutable collections; code that changed one
  in place must now assign a new collection.
- The stored JSON is the same for strings, lists, sets and maps, so existing rows read unchanged.
