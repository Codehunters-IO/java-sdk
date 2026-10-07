# Changelog — sdk-measure-micrometer

## 3.0.0

### Breaking
- Meter names and tags follow Micrometer's conventions: one timer per name with `class`, `method`,
  `exception` and `outcome` tags. The `<name>.errors` counter is gone; the long-task timer is
  `<name>.active` (was `<name>.long-task`) and the counter `<name>.counted` (was `<name>.count`).
  The default name is `method.measured` (was `SimpleClassName.methodName`).
- Tag keys are stable: a `null` or blank value is `none` and a failed expression `unknown`, where
  2.x dropped the tag.
- Expressions run in a read-only `SimpleEvaluationContext`: method calls, assignments, type
  references, constructors and bean references are no longer available.
- A malformed `@Measured` (odd tags, an expression without `=`, a blank, repeated, reserved or
  malformed key, a count value that is not positive and finite) fails startup; 2.x ignored it. So
  do two annotations sharing a name with different tag keys.

### Added
- `@Measured` on a class measures the methods declared in it.
- `CompletionStage` results are measured until they complete.
- `codehunters.measure.enabled` and `codehunters.measure.max-tag-values`; expression tags beyond
  the cap are recorded as `other`.
- `MeasureAspect(MeterRegistry, int)`, `MeasureProperties`, `MeasuredBeanValidator`, which also
  warns about annotations that are never measured.

### Fixed
- The aspect was never registered in a real application: the auto-configuration ran before Boot's
  metrics auto-configuration, so its `@ConditionalOnBean(MeterRegistry)` never matched.
- The auto-configuration applied `@EnableAspectJAutoProxy` to the whole application.
- A SpEL error in a tag or in `countCondition` failed the measured call.
- Each call parsed its expressions again; the annotation, tags and parameter names are now worked
  out once per method.
