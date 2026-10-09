# Migrating sdk-measure-micrometer 3.x to 4.0

4.0 reorganises packages for readability. Behaviour, meter names and tags, the
`codehunters.measure.*` properties and the auto-configuration are unchanged; only package names
move.

`@Measured` stays in `io.codehunters.measure`. An application that only annotates methods needs no
change. The auto-configuration, its properties and the startup validator move to
`io.codehunters.measure.config`; the aspect and everything it uses move to
`io.codehunters.measure.internal`, which is not API and may change in any release.

| 3.x | 4.0 |
|---|---|
| `io.codehunters.measure.Measured` | unchanged |
| `io.codehunters.measure.MeasureAutoConfiguration` | `io.codehunters.measure.config.MeasureAutoConfiguration` |
| `io.codehunters.measure.MeasureProperties` | `io.codehunters.measure.config.MeasureProperties` |
| `io.codehunters.measure.MeasuredBeanValidator` | `io.codehunters.measure.config.MeasuredBeanValidator` |
| `io.codehunters.measure.MeasureAspect` | `io.codehunters.measure.internal.MeasureAspect` |
| `io.codehunters.measure.MeasuredDefinition` | `io.codehunters.measure.internal.MeasuredDefinition` |
| `io.codehunters.measure.MeasuredExpression` | `io.codehunters.measure.internal.MeasuredExpression` |
| `io.codehunters.measure.Measurement` | `io.codehunters.measure.internal.Measurement` |
| `io.codehunters.measure.MethodPlan` | `io.codehunters.measure.internal.MethodPlan` |
| `io.codehunters.measure.RecordingMeasurement` | `io.codehunters.measure.internal.RecordingMeasurement` |
| `io.codehunters.measure.TagCardinalityLimiter` | `io.codehunters.measure.internal.TagCardinalityLimiter` |
| `io.codehunters.measure.WarnOnce` | `io.codehunters.measure.internal.WarnOnce` |

Update these if you have them:

- A `MeasureAspect` or `MeasuredBeanValidator` bean of your own that replaces the SDK's, or an
  import of `MeasureProperties`: import the new package.
- `@ImportAutoConfiguration`, `exclude`, `spring.autoconfigure.exclude`, `afterName` or
  `beforeName` naming `io.codehunters.measure.MeasureAutoConfiguration`: use
  `io.codehunters.measure.config.MeasureAutoConfiguration`.
- Logging configuration for the SDK's loggers: the warnings now log under
  `io.codehunters.measure.internal`.

Only imports change; properties are unchanged. In the IDE, "organise imports" fixes the rest.
