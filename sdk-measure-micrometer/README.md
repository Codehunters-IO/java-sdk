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
    countCondition = "#result.accepted()")
public Receipt place(PlaceOrder command) { ... }
```

On a class, `@Measured` applies to each method declared in it that has no `@Measured` of its own.
Leave `name` blank there: all methods then share the timer `method.measured` and the `class` and
`method` tags tell them apart.

## Meters

| Meter | Type | Tags |
|---|---|---|
| `<name>` (default `method.measured`) | Timer | `class`, `method`, `exception`, `outcome`, plus annotation tags |
| `<name>.active` (with `longTask = true`, instead of the timer) | Long-task timer | `class`, `method`, plus annotation tags |
| `<name>.counted` (with `count = true`) | Counter, incremented by `countValue` | `class`, `method`, plus annotation tags |

- `class` is the fully qualified name of the declaring class, `method` the method name.
- `outcome` is `SUCCESS` or `ERROR`. `exception` is the simple class name of the exception, or
  `none` on success or when `recordExceptions = false`.
- Every call of a method records the same tag keys:
  - a blank static value, or an expression that yields `null` or blank, is `none`;
  - an expression that fails to parse or evaluate is `unknown`.
- A method that returns a `CompletionStage` is measured until the stage completes, and the stage is
  returned unchanged. A `CompletionException` is unwrapped for the `exception` tag. Other
  asynchronous types (`Mono`, `Flux`) are measured until the method returns.
- The counter only increments after a successful call whose `countCondition` holds. A condition
  that fails to evaluate does not hold.

## Expressions

Expressions are SpEL, run in a `SimpleEvaluationContext`. They can read properties, index maps and
lists, and call instance methods. They cannot reference types (`T(...)`), call constructors or
look up beans. Arguments are available by name (`#command`) and position (`#p0`, `#a0`, `#arg0`);
`countCondition` also sees `#result`, which hides a parameter called `result`. Each annotation is
parsed once.

Measuring never changes the call: the result or exception reaches the caller unchanged. A failed
expression, or a meter the registry rejects, is logged as a warning once and skipped.

## Validation at startup

The application fails to start when a bean carries a malformed `@Measured`:

- `tags` with an odd number of entries;
- an expression that is not `key=expression`;
- a blank or repeated tag key;
- a reserved tag key: `class`, `method`, `exception`, `outcome`.

An expression that does not parse is not rejected: it is logged and recorded as `unknown`.

## Cardinality

Each expression tag takes at most `codehunters.measure.max-tag-values` distinct values per meter
(default 100). Further values are recorded as `other`, so the meter still counts every call. The
first overflow of each tag is logged. Keep expression tags to bounded values such as a status or a
channel, never an id.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `codehunters.measure.enabled` | `true` | Register the aspect and validate annotations |
| `codehunters.measure.max-tag-values` | `100` | Distinct values per expression tag and meter; at least 1 |

## Known limits

- Registries such as Prometheus require every meter with a given name to have the same tag keys.
  Two methods that share a `name` but declare different tags are rejected by such a registry;
  give them different names. The rejection is logged once and the call is not affected.
- `@Measured` on a class covers the methods it declares, not those it inherits.
- Spring AOP proxies only intercept calls made through the bean: a method calling another
  measured method of the same class is not measured again.

## Upgrading from 2.x

Meter names follow Micrometer's conventions in 3.0.0, which breaks dashboards and alerts; see
[MIGRATION-3.0.md](MIGRATION-3.0.md).
