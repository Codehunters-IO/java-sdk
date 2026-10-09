# Migrating sdk-audit-log 3.x to 4.0

4.0.0 splits the 24 classes of `io.codehunters.audit` by concern. What a service uses stays in
`io.codehunters.audit`; auto-configuration, the aspect, asynchronous result handling and the
publishers move to sub-packages. Only imports change: class names, properties
(`codehunters.audit.*`), the legacy `vitxo.audit.fail-on-error` alias and behaviour are those of
3.1.0.

## Unchanged: `io.codehunters.audit`

`Auditable`, `AuditEvent`, `AuditEventPublisherPort`, `AuditContextProvider`,
`NoOpAuditContextProvider`, `AuditEventFailureListener`, `AuditEventRejectionListener`,
`AuditPersistenceException`. Code that only annotates methods, implements `AuditContextProvider` or
`AuditEventPublisherPort`, or registers a listener needs no change.

## Moved

| 3.x | 4.0 |
|---|---|
| `io.codehunters.audit.AuditAutoConfiguration` | `io.codehunters.audit.config.AuditAutoConfiguration` |
| `io.codehunters.audit.AuditJdbcAutoConfiguration` | `io.codehunters.audit.config.AuditJdbcAutoConfiguration` |
| `io.codehunters.audit.AuditProperties` | `io.codehunters.audit.config.AuditProperties` |
| `io.codehunters.audit.AuditLegacyProperties` | `io.codehunters.audit.config.AuditLegacyProperties` |
| `io.codehunters.audit.AuditAspect` | `io.codehunters.audit.internal.aspect.AuditAspect` |
| `io.codehunters.audit.AsyncAuditEventPublisher` | `io.codehunters.audit.persistence.AsyncAuditEventPublisher` |
| `io.codehunters.audit.FailSafeAuditEventPublisher` | `io.codehunters.audit.persistence.FailSafeAuditEventPublisher` |

`io.codehunters.audit.persistence` keeps its 3.x classes (`AuditEventJdbcAdapter`,
`AutoCommitDataSource`, `JsonSnapshotAuditEventPublisher`, `RequiresNewAuditEventPublisher`,
`TransactionAwareAuditEventPublisher`).

Implementation classes that were package-private in 3.x also moved: `CapturedCall`,
`AuditSpelEvaluator`, `AuditProxyCheck` and `AuditMdcTaskDecorator` to `audit.internal.aspect`;
`AsyncResults`, `ReactorResults` and `ReactorSecurityActor` to `audit.internal.reactive`;
`DeviceAuditContextProvider` to `audit.internal.device`. Some are now public so the auto-configuration can
reach them; they are not API and may change in any release.

## Steps

1. **Dependency.** Bump `sdk-audit-log` to 4.0.0, or the `sdk-bom` that manages it. With `sdk-web`
   on the classpath, use sdk-web 2.0.0: 4.0 names its `io.codehunters.web.device` classes.
2. **Imports.** Update the imports of the classes in the table. In IntelliJ IDEA, delete the stale
   import and let *Optimize Imports* / *Add import* pick the new package.
3. **Names in strings.** `@AutoConfigureAfter(name = ...)`, `excludeName` and
   `spring.autoconfigure.exclude` entries naming `AuditAutoConfiguration` or
   `AuditJdbcAutoConfiguration` need the `config` package.

The `vitxo.audit.fail-on-error` key is now honoured until 5.0.0 (was 4.0.0).
