# sdk-measure-micrometer

`@Measured` records Micrometer meters for a method, or for every method of a class.

```kotlin
implementation("io.codehunters:sdk-measure-micrometer:3.0.0")
```

The application needs Boot's metrics auto-configuration (for example through
`spring-boot-starter-actuator`) and AspectJ on the classpath. Boot's AOP auto-configuration
applies the aspect; the SDK does not enable proxying itself.

## Usage

```java
@Measured(
    name = "orders.place",
    tags = {"channel", "web"},
    expressions = {"customer=#command.customerId"},
    count = true,
    countCondition = "#result.accepted")
public Receipt place(PlaceOrder command) { ... }
```

On a class, `@Measured` applies to each method declared in it that has no `@Measured` of its own,
including `equals`, `hashCode` and `toString` when the class overrides them. An annotation on a
superclass also covers the methods that superclass declares. With a blank `name`, every method shares
the timer `method.measured` and the `class` and `method` tags tell them apart. That only works while
every annotation using the name declares the same tag keys, so give an annotation with `tags` or
`expressions` an explicit name.

## Meters

| Meter | Type | Tags |
|---|---|---|
| `<name>` (default `method.measured`) | Timer | `class`, `method`, `exception`, `outcome`, plus annotation tags |
| `<name>.active` (with `longTask = true`, instead of the timer) | Long-task timer | `class`, `method`, plus annotation tags |
| `<name>.counted` (with `count = true`) | Counter, incremented by `countValue` | `class`, `method`, plus annotation tags |

- `class` is the fully qualified name of the class that declares the method, not of the bean: two
  beans inheriting a measured method from one base class share its series. `method` is the method
  name.
- `outcome` is `SUCCESS` or `ERROR`. `exception` is the simple class name of the exception, or
  `none` on success or when `recordExceptions = false`.
- Every call of a method records the same tag keys:
  - a blank static value, or an expression that yields `null` or blank, is `none`;
  - an expression that fails to parse or evaluate is `unknown`.
- A method that returns a `CompletionStage` is measured until the stage completes, and the stage is
  returned unchanged. A `CompletionException` is unwrapped for the `exception` tag, and a cancelled
  stage is an `ERROR` with `exception=CancellationException`. A stage that never completes keeps
  its arguments and, with `longTask = true`, its active task forever. Other asynchronous types
  (`Mono`, `Flux`) are measured until the method returns.
- The counter only increments after a successful call whose `countCondition` holds. A condition
  that fails to evaluate does not hold.

## Expressions

Expressions are SpEL, run in a read-only `SimpleEvaluationContext`. They can read properties
(JavaBean getters and record components, as `#order.customer`) and index maps and lists. They
cannot call methods, assign, reference types (`T(...)`), call constructors or look up beans: such an
expression is recorded as `unknown`. Expressions run before the method, so this keeps them from
consuming or changing its arguments. A value is converted to a string with its `toString()`, so tag
on a property, never on a whole object. Arguments are available by name (`#command`) and position (`#p0`, `#a0`, `#arg0`);
`countCondition` also sees `#result`, which hides a parameter called `result`. Each annotation is
parsed once.

Measuring never changes the call: the result or exception reaches the caller unchanged. A failed
expression, or a meter the registry rejects, is logged as a warning once and skipped.

## Validation at startup

The application fails to start when a bean carries a malformed `@Measured`:

- `tags` with an odd number of entries;
- an expression that is not `key=expression`;
- a blank or repeated tag key, or one that does not match `[A-Za-z_][A-Za-z0-9_.-]*`;
- a reserved tag key: `class`, `method`, `exception`, `outcome`;
- a `countValue` that is not positive and finite;
- two annotations that share a meter name but declare different tag keys. Registries such as
  Prometheus keep one set of tag keys per name and drop the other series without an error.

Class annotations are checked on the bean's class and its superclasses. A `@Measured` that is
never measured, on a private, final or static method or only on an interface, is logged as a
warning.

An expression that does not parse is not rejected: it is logged and recorded as `unknown`.

## Cardinality

Each expression tag takes at most `codehunters.measure.max-tag-values` distinct values per meter
name and method (default 100); methods sharing a name each have their own budget. Further values are recorded as `other`, so the meter still counts every call. The
first overflow of each tag is logged. Keep expression tags to bounded values such as a status or a
channel, never an id.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `codehunters.measure.enabled` | `true` | Register the aspect and validate annotations |
| `codehunters.measure.max-tag-values` | `100` | Distinct values per expression tag, meter name and method; at least 1 |

## Known limits

- A meter the registry rejects for another reason is logged once per name and method, and the call
  is not affected.
- `@Measured` on a class covers the methods it and its annotated superclasses declare, not methods
  inherited from an unannotated superclass.
- Spring AOP proxies only intercept calls made through the bean: a method calling another
  measured method of the same class is not measured again.

## Upgrading from 2.x

Meter names follow Micrometer's conventions in 3.0.0, which breaks dashboards and alerts; see
[MIGRATION-3.0.md](MIGRATION-3.0.md).
