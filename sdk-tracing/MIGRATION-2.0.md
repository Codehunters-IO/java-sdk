# Migrating sdk-tracing to 2.0

2.0 reorganises the Java packages by capability. Only imports change; behaviour, beans and the
`codehunters.tracing.*` properties are unchanged.

The public API (`TraceContextPort`, `TraceParent`, `TraceHeaders`) and the auto-configurations
(`io.codehunters.tracing.config.*`) keep their packages, so most applications need no change.

| 1.x | 2.0 |
|---|---|
| `io.codehunters.tracing.TraceIdFilter` | `io.codehunters.tracing.servlet.TraceIdFilter` |
| `io.codehunters.tracing.MdcTaskDecorator` | `io.codehunters.tracing.mdc.MdcTaskDecorator` |
| `io.codehunters.tracing.MdcTraceContextAdapter` | `io.codehunters.tracing.mdc.MdcTraceContextAdapter` |
| `io.codehunters.tracing.TracePropagationMessagePostProcessor` | `io.codehunters.tracing.amqp.TracePropagationMessagePostProcessor` |
| `io.codehunters.tracing.InboundTraceMethodInterceptor` | `io.codehunters.tracing.amqp.InboundTraceMethodInterceptor` |
| `io.codehunters.tracing.RabbitTracingBeanPostProcessor` | `io.codehunters.tracing.amqp.RabbitTracingBeanPostProcessor` |
| `io.codehunters.tracing.TraceContextPort`, `TraceParent`, `TraceHeaders` | unchanged |
| `io.codehunters.tracing.config.*` | unchanged |

`io.codehunters.tracing.internal` holds implementation shared by the SDK; it is not API and may
change in any release.

## How

1. Bump `io.codehunters:sdk-tracing` (or the BOM) to 2.0.0.
2. Recompile. Fix the imports the compiler reports; your IDE's "organise imports" resolves them from
   the table above.

## Logging

Logger categories follow the classes, so a level set on an old package or class name no longer
applies. Rename it the same way as the imports, for example:

```properties
# before
logging.level.io.codehunters.tracing.TraceIdFilter=DEBUG
# after
logging.level.io.codehunters.tracing.servlet.TraceIdFilter=DEBUG
```

A level on `io.codehunters` keeps working.
