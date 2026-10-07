# Migrating sdk-measure-micrometer 2.x to 3.0

1. **Measuring now happens.** 2.x registered its aspect only when the application declared a
   `MeterRegistry` bean of its own; with Boot's metrics auto-configuration nothing was measured.
   3.0 measures every `@Measured` method once Boot's metrics are on, so expect new series.
2. **Meter names.** Dashboards and alerts must move to the new names:

   | 2.x | 3.0 |
   |---|---|
   | Timer `<name>`, annotation tags | Timer `<name>`, tags `class`, `method`, `exception`, `outcome` + annotation tags |
   | Counter `<name>.errors`, tag `exception` | Timer `<name>` with `outcome="ERROR"`; its count is the error count |
   | Long-task timer `<name>.long-task` | Long-task timer `<name>.active` |
   | Counter `<name>.count` | Counter `<name>.counted`, tags `class`, `method` + annotation tags |
   | Default name `SimpleClassName.methodName` | Default name `method.measured`, told apart by `class` and `method` |

   In Prometheus, `<name>` becomes `<name>_seconds_count`, `_sum` and `_max`;
   `<name>_errors_total` becomes `<name>_seconds_count{outcome="ERROR"}`.
3. **Exception tag.** The `exception` tag is on the timer and holds the simple class name, or
   `none`. With `recordExceptions = false` it is always `none`.
4. **Tags with no value.** A static tag with a blank value, or an expression that yields `null`,
   is recorded as `none`; a failed expression as `unknown`. 2.x left the tag out, so the same
   meter had different tag sets.
5. **Expressions.** Method calls, assignments, `T(...)`, constructors and `@bean` references no
   longer evaluate; they are recorded as `unknown` and logged. Read the property instead:
   `#order.getCustomer()` and `#order.customer()` become `#order.customer`. Indexing still works.
6. **Startup validation.** Fix any `@Measured` that fails startup: tags must be key/value pairs,
   expressions `key=expression`, keys unique, matching `[A-Za-z_][A-Za-z0-9_.-]*` and not one of
   `class`, `method`, `exception` or `outcome`, and `countValue` positive and finite. Annotations
   that share a name, including the default `method.measured`, must declare the same tag keys.
7. **Cardinality.** Expression tags take at most 100 distinct values per meter name and method by
   default
   (`codehunters.measure.max-tag-values`); further values become `other`.
8. **Proxying.** The SDK no longer applies `@EnableAspectJAutoProxy`. Boot's AOP
   auto-configuration does, unless `spring.aop.auto=false`; then enable proxying yourself.
