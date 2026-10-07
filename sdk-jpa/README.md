# sdk-jpa

Building blocks for JPA persistence adapters on Spring Boot 4 and Hibernate 7: audited base
entities with time-ordered ids, a stable page response, a sort whitelist, JSON attribute converters
and whitelisted search specifications ([ADR 0013](../docs/adr/0013-jpa-building-blocks.md)).

```kotlin
implementation(platform("io.codehunters:sdk-bom:<version>"))
implementation("io.codehunters:sdk-jpa")
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
```

Spring Data JPA, Hibernate, Jackson 3 and Spring Security are not brought in: the application
already has the ones it uses.

**Where it belongs.** Everything here is for the infrastructure layer. Entities extending
`BaseEntity` are persistence models; the domain model stays free of JPA and of this module, and the
adapter maps between them. Use cases receive `PageResponse` or `SearchCriteria` only if your ports
are shaped that way; otherwise map at the adapter boundary.

## Base entities

```java
@Entity
@Table(name = "orders")
public class OrderEntity extends AuditableEntity {
  @Column(name = "reference")
  private String reference;
  // ...
}
```

| Class | Columns | What it gives |
|---|---|---|
| `BaseEntity` | `id uuid`, `version bigint` | UUID version 7 id generated on persist (time ordered, index friendly), optimistic locking |
| `AuditableEntity` | adds `created_at`, `created_by`, `updated_at`, `updated_by` | who created and last changed the row, and when |

- **Ids.** `getId()` and `getVersion()` are `Optional` and empty until the entity is persisted;
  `isNew()` tells the same. Hibernate maps fields, so the getters are free to return `Optional`.
- **Equality.** Two entities are equal when they have the same class and id. An entity that was
  never persisted equals only itself. Hibernate proxies compare by their identifier without being
  initialized. `hashCode()` is one value per entity class, so an entity stays findable in a
  `HashSet` while it goes from new to persisted; the price is that a hash set of many entities of
  one class degrades to a list. Calling `hashCode()` on an uninitialized proxy initializes it.
- **Version conflicts.** Saving a stale copy throws `ObjectOptimisticLockingFailureException`.
- **Columns.** Rename them in a subclass with `@AttributeOverride`. Reference DDL for PostgreSQL:

```sql
create table orders (
  id         uuid primary key,
  version    bigint not null,
  created_at timestamp with time zone,
  created_by varchar(255),
  updated_at timestamp with time zone,
  updated_by varchar(255),
  reference  varchar(255)
);
```

  On SQL Server use `uniqueidentifier` and `datetimeoffset(6)`.

## Auditing

With JPA on the classpath and an `EntityManagerFactory` bean, the SDK enables Spring Data JPA
auditing:

- **Time:** the application's `Clock` bean when there is exactly one, the UTC system clock
  otherwise, cut to microseconds (the precision PostgreSQL and SQL Server keep).
- **Author:** with Spring Security, the signed-in user's `Authentication.getName()`, which is the
  JWT `sub` for a resource server. Anonymous or unauthenticated callers, and work outside a request
  (schedulers, listeners), leave the author empty.
- **Overriding:** a `DateTimeProvider` or `AuditorAware` bean of the application replaces the SDK's.
  If the application declares `@EnableJpaAuditing` itself, the SDK adds nothing.

```yaml
codehunters:
  jpa:
    auditing:
      enabled: true   # false: the SDK does not enable auditing
```

## Pages and sorting

```java
private static final SortWhitelist SORTS =
    SortWhitelist.of(Map.of("created", "createdAt", "customer", "customer.name"));

@GetMapping("/orders")
PageResponse<OrderView> list(Pageable pageable) {
  return PageResponse.from(orders.findAll(SORTS.apply(pageable)), OrderView::of);
}
```

- **`PageResponse<T>`** is a record with a stable JSON shape: `content`, `page` (zero based),
  `size`, `totalElements`, `totalPages` and `sort` (`[{"property":"createdAt","direction":"DESC"}]`).
  `from(page, mapper)` maps each element; a mapper returning `null` is rejected.
- **`SortWhitelist`** maps API sort names to entity property paths, keeps direction, case and null
  handling, and rejects anything else, and the same name twice, with an `IllegalArgumentException`
  that lists the allowed names without repeating the request. Clients cannot sort by internal
  columns or make the query fail.
- **Page size.** The cap is Spring Data's: `spring.data.web.pageable.max-page-size` (2000 by
  default). Set it to what your queries can afford.

## JSON attribute converters

Store a value as JSON text in one column:

```java
@Convert(converter = StringListConverter.class)
@Column(name = "tags", length = 2000)
private List<String> tags;

@Converter
public class AddressConverter extends JsonAttributeConverter<Address> {
  public AddressConverter() {
    super(new TypeReference<>() {});
  }
}
```

| Converter | Attribute | Column |
|---|---|---|
| `StringListConverter` | `List<String>` | JSON array, order and `null` elements kept |
| `StringSetConverter` | `Set<String>` | JSON array; reads keep order and drop duplicates |
| `StringMapConverter` | `Map<String, String>` | JSON object, entry order and `null` values kept |
| `JsonListConverter<E>`, `JsonSetConverter<E>`, `JsonMapConverter<V>` | subclass with the element class | as above |
| `JsonAttributeConverter<T>` | subclass with a `TypeReference` | any type, generics included |

- `null` is stored as a `null` column; a `null`, blank or `null`-literal column is read as `null`.
- Collections read from a column cannot be changed in place. Assign a new collection: that is also
  what Hibernate needs to see the change.
- Unknown JSON properties are ignored, so removing a field from the type does not break old rows.
- A shared default `JsonMapper` is used. With Spring Boot, Hibernate creates converters through
  Spring, so a subclass whose constructor takes a `JsonMapper` receives the application's.
- Errors raise `JsonColumnException`, naming the Java type and where parsing stopped. The column
  content and the Jackson cause, whose message quotes it, are never included.
- **When not to use them.** For a real `json`/`jsonb` column you query into, prefer Hibernate's
  native mapping, `@JdbcTypeCode(SqlTypes.JSON)`. These converters suit text columns and schemas
  that must work on several databases.

## Search specifications

```java
private static final SpecificationBuilder<OrderEntity> SEARCH =
    SpecificationBuilder.of(
        OrderEntity.class,
        Map.of("status", "status", "placed", "placedAt", "customer", "customer.name"));

orders.findAll(SEARCH.allOf(List.of(
    SearchCriteria.of("status", SearchOperation.IN, "OPEN", "PAID"),
    SearchCriteria.of("placed", SearchOperation.GTE, "2026-10-01T00:00:00Z"))), pageable);
```

| Operation | Values | Meaning |
|---|---|---|
| `EQUAL`, `NOT_EQUAL` | 1 | equal, not equal (rows where the attribute is null never match `NOT_EQUAL`) |
| `LIKE` | 1 | text containing the value, ignoring case; `%` and `_` match themselves |
| `BETWEEN` | 2 | both ends included |
| `IN` | 1 to 1000 | equal to one of the values |
| `GT`, `GTE`, `LT`, `LTE` | 1 | compared; the attribute must be orderable |
| `IS_NULL`, `IS_NOT_NULL` | 0 | null checks; the only operations allowed on an association itself |

- **Whitelist.** Only listed API names can be searched, each mapped to an entity property path.
  Anything else is rejected before a query is built.
- **Values.** Text is converted to the attribute's type with Spring Boot's shared
  `ApplicationConversionService` (enums by name, numbers, `UUID`, ISO-8601 instants and dates).
  `withConversionService(...)` uses another one.
- **Associations.** A path such as `customer.name` left-joins `customer`, once per query however
  many conditions use it, so rows without a customer still match an `OR` on another attribute.
  Joining a collection (`orders.reference`) makes the query distinct, so each root row comes back
  and is counted once. On PostgreSQL, a distinct query cannot be sorted by a property of the joined
  collection.
- **Combining.** `allOf` (AND) and `anyOf` (OR) take up to 32 criteria; none means no restriction.
  Compose further with `Specification.and`/`or`.
- **Errors.** `InvalidSearchException` (an `IllegalArgumentException`) never repeats the client's
  input. Whitelist and value-count errors are thrown when the specification is built; conversion
  and unsupported-operation errors when the query runs, where Spring wraps them in
  `InvalidDataAccessApiUsageException`. Map it to a 400 by looking for it in the cause chain.
- **Indexes.** `LIKE` compares `lower(column)` with a leading `%`: no plain B-tree index helps.
  Searchable text columns on large tables need a trigram (`pg_trgm`) or full-text index.

## Known limits

- `BaseEntity` uses UUID ids only. Tables with numeric identity ids keep their own mapping.
- Auditing has no author outside a security context; set one with your own `AuditorAware` if
  batch jobs must be attributed.
- `created_by`/`updated_by` hold up to 255 characters, the default column length.
- Search conditions on embedded values work only directly under the root or a joined entity, not
  through an embedded value nested in another embedded value.
