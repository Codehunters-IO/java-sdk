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
already has the ones it uses. `sdk-commons` is (an `api` dependency since 3.0.0): the sort and
search exceptions are its `ClientInputException`s.

**Where it belongs.** Everything here is for the infrastructure layer. Entities extending
`BaseEntity` are persistence models; the domain model stays free of JPA and of this module, and the
adapter maps between them. Use cases receive `PageResponse` or `SearchCriteria` only if your ports
are shaped that way; otherwise map at the adapter boundary.

## Base entities

`BaseEntity` and `AuditableEntity` are in `io.codehunters.jpa.entity` (`io.codehunters.jpa.domain`
before 2.0.0; see [MIGRATION-2.0.md](MIGRATION-2.0.md)).

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

- **Ids.** Accessors are record style: `id()` and `version()` (and `createdAt()`, `createdBy()`,
  `updatedAt()`, `updatedBy()` on `AuditableEntity`) return `Optional`, empty until the entity is
  persisted. They are not JavaBean getters on purpose: Spring reads `getVersion()` as a bean
  property, and an `Optional` there would make every new entity look persisted.
- **Saving.** `BaseEntity` is a Spring Data `Persistable`, new while it has no id, so
  `repository.save(entity)` persists that instance (Hibernate assigns its id) and merges only
  entities that were persisted before. `getId()` exists for that contract and returns `null` while
  the entity is new; prefer `id()`.
- **Equality.** Two entities are equal when they have the same class and id. An entity that was
  never persisted equals only itself. `equals`, `hashCode`, `id()` and `isNew()` are final, so on an
  uninitialized Hibernate proxy they read its identifier without loading the row, also after the
  session closed. `version()` on such a proxy loads it. `hashCode()` is one value per entity class,
  so an entity stays findable in a `HashSet` while it goes from new to persisted; the price is that
  a hash set of many entities of one class degrades to a list.
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

  PostgreSQL is the only supported database.

## Auditing

Auditing is **opt-in**. `@EnableJpaAuditing` applies to every entity of the application, not only
to the ones extending `AuditableEntity`, and the SDK's `AuditorAware` reads the security context, so
adding the jar never switches it on by itself. With `codehunters.jpa.auditing.enabled=true`, JPA on
the classpath and an `EntityManagerFactory` bean, the SDK enables Spring Data JPA auditing and logs
it once at startup (`SDK JPA auditing is on`):

- **Time:** the application's `Clock` bean when there is exactly one, the UTC system clock
  otherwise, cut to microseconds. PostgreSQL keeps exactly microseconds, so the value round-trips.
- **Author:** with Spring Security, the signed-in user's `Authentication.getName()`, which is the
  JWT `sub` for a resource server. Anonymous or unauthenticated callers, and work outside a request
  (schedulers, listeners), leave the author empty.
- **Overriding:** a `DateTimeProvider` or `AuditorAware` bean of the application replaces the SDK's.
  If the application declares `@EnableJpaAuditing` itself, the SDK adds nothing.

```yaml
codehunters:
  jpa:
    auditing:
      enabled: true   # default false: the SDK does not enable auditing
```

When entities extend `AuditableEntity` and no auditing is active (the property is off and the
application has no `@EnableJpaAuditing` of its own), startup logs a `WARN` naming them: their audit
columns would otherwise stay empty without any error.

## Pages and sorting

```java
private static final SortWhitelist SORTS =
    SortWhitelist.of(Map.of("created", "createdAt", "customer", "customer.name"));

@GetMapping("/orders")
PageResponse<OrderView> list(Pageable pageable) {
  return PageResponse.from(orders.findAll(SORTS.apply(pageable)), OrderView::of, SORTS);
}
```

- **`PageResponse<T>`** is a record with a stable JSON shape: `content`, `page` (zero based),
  `size`, `totalElements`, `totalPages` and `sort` (`[{"property":"created","direction":"DESC"}]`).
  `from(page, mapper)` maps each element; a mapper returning `null` is rejected.
  `from(page, mapper, sorts)` reports the sort under the whitelist's API names, the ones a client
  can send back, and leaves out orders the whitelist does not name; without the whitelist the
  `sort` lists entity property paths.
- **`SortWhitelist`** maps API sort names to entity property paths, keeps direction, case and null
  handling, and rejects anything else, and the same name twice, with an `InvalidSortException`
  (code `INVALID_SORT`) that lists the allowed names without repeating the request. Clients cannot
  sort by internal columns or make the query fail. It is a `ClientInputException` from
  `sdk-commons`, and still an `IllegalArgumentException`; `sdk-web` answers it with 400. An invalid
  whitelist (`SortWhitelist.of(...)`) is a programming error: a plain `IllegalArgumentException`.
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

- **Values need value-based `equals`.** Hibernate checks a converted attribute for changes by
  converting it and comparing with `equals`. Records, strings, lists, sets and maps compare by value;
  a class without `equals` looks changed on every flush, so each transaction that merely loads the
  entity writes it and bumps its `version`.
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
| `LIKE` | 1 | text containing the value (up to 256 characters), ignoring case; `%` and `_` match themselves |
| `BETWEEN` | 2 | both ends included |
| `IN` | 1 to 1000 | equal to one of the values |
| `GT`, `GTE`, `LT`, `LTE` | 1 | compared; the attribute must be orderable, and not an enum |
| `IS_NULL`, `IS_NOT_NULL` | 0 | null checks; the only operations allowed on an association itself (on a collection: empty or not) |

- **Whitelist.** Only listed API names can be searched, each mapped to an entity property path.
  Anything else is rejected before a query is built.
- **Values.** Text is converted to the attribute's type with Spring Boot's shared
  `ApplicationConversionService` (enums by name, numbers, `UUID`, ISO-8601 instants and dates).
  `withConversionService(...)` uses another one.
- **Associations.** A path such as `customer.name` left-joins `customer`, once per query however
  many conditions use it, so rows without a customer still match an `OR` on another attribute.
- **Collections.** A path through a collection (`lines.sku`) becomes an `EXISTS` subquery
  correlated with the row, one per condition: "some line has this SKU". Each condition matches its
  own element, so `sku = A` and `sku = B` find an order with one line of each. The outer query is
  never `distinct`, so each row is returned and counted once and the page can be sorted by any
  whitelisted property, an association's included. `IS_NULL` on a value inside a collection means
  "some element has it null".
- **Combining.** `allOf` (AND) and `anyOf` (OR) take up to 32 criteria; none means no restriction.
  Compose further with `Specification.and`/`or`.
- **Errors.** `InvalidSearchException` (code `INVALID_SEARCH`) never repeats the client's input. It
  is a `ClientInputException` from `sdk-commons`, and still an `IllegalArgumentException`.
  Whitelist and value-count errors are thrown when the specification is built; conversion and
  unsupported-operation errors when the query runs, where Spring wraps them in
  `InvalidDataAccessApiUsageException`. `sdk-web` 3.0.0 finds it in the cause chain and answers
  400 with the code; without `sdk-web`, look for it in the cause chain. An invalid whitelist
  (`SpecificationBuilder.of(...)`) is a programming error: a plain `IllegalArgumentException`.
- **Indexes.** `LIKE` compares `lower(column)` with a leading `%`: no plain B-tree index helps.
  Searchable text columns on large tables need a trigram (`pg_trgm`) or full-text index.

## Known limits

- `BaseEntity` uses UUID ids only. Tables with numeric identity ids keep their own mapping.
- Auditing has no author outside a security context; set one with your own `AuditorAware` if
  batch jobs must be attributed.
- `created_by`/`updated_by` hold up to 255 characters, the default column length.
- Search conditions on embedded values work only directly under the root or a joined entity, not
  through an embedded value nested in another embedded value.
- With entity inheritance, a proxy created for the parent type reports the parent as its class and
  does not equal a loaded subtype instance of the same row. Compare `id()` when that matters.
- A `Specification` restored from serialized form converts values with Spring Boot's shared
  `ApplicationConversionService`; a custom conversion service is not serialized.
