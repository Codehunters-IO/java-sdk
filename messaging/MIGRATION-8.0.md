# Migrating sdk-event-messaging to 8.0

8.0 reorganises the Java packages by capability instead of hexagonal layers. **Only imports change**:
behaviour, beans, the `codehunters.outbox.*` properties, the DDL and the reference scripts under
`db/migration/outbox/**` are unchanged. The four artifacts (`-core`, `-jdbc`, `-rabbit`, `-kafka`)
move to 8.0.0 together; import the matching `io.codehunters:sdk-bom` to keep them aligned.

Each artifact now owns its packages, so no package is split across jars any more: the transport
auto-configurations left the shared `io.codehunters.messaging.config`.

**Coming from 6.x?** Apply [MIGRATION-7.0.md](MIGRATION-7.0.md) first (the `send_started_at`
column), then this guide. From 5.x, start with [MIGRATION-6.0.md](MIGRATION-6.0.md).

## Checklist

1. Bump the dependencies (or the BOM) to 8.0.0.
2. Recompile and fix the imports the compiler reports. Your IDE's "organise imports" resolves them
   from the tables below.
3. Update any class name written as a string: `@AutoConfiguration(after/before = ...)`,
   `@ImportAutoConfiguration`, `spring.autoconfigure.exclude`, `@ComponentScan` filters, logging
   categories (`logging.level.io.codehunters.messaging...`).
4. Optional: rename the old `outbox.*` keys. They are honoured until 9.0 (they were due to go in 8.0).

## sdk-event-messaging-core

| 7.x | 8.0 |
|---|---|
| `io.codehunters.messaging.domain.model.EventMessage` | `io.codehunters.messaging.EventMessage` |
| `io.codehunters.messaging.domain.model.EventTypes` | `io.codehunters.messaging.EventTypes` |
| `io.codehunters.messaging.domain.model.OutboxMessage` | `io.codehunters.messaging.OutboxMessage` |
| `io.codehunters.messaging.domain.model.OutboxStatus` | `io.codehunters.messaging.OutboxStatus` |
| `io.codehunters.messaging.application.port.MessagePublishException` | `io.codehunters.messaging.MessagePublishException` |
| `io.codehunters.messaging.application.port.MessagePublisherPort` | `io.codehunters.messaging.MessagePublisherPort` |
| `io.codehunters.messaging.application.port.OutboxRepositoryPort` | `io.codehunters.messaging.OutboxRepositoryPort` |
| `io.codehunters.messaging.application.port.SendOutboxMessagesPort` | `io.codehunters.messaging.SendOutboxMessagesPort` |
| `io.codehunters.messaging.config.MessagePublisherDecorator` | `io.codehunters.messaging.MessagePublisherDecorator` |
| `io.codehunters.messaging.domain.factory.EventMessageFactory` | `io.codehunters.messaging.factory.EventMessageFactory` |
| `io.codehunters.messaging.domain.factory.OutboxEventFactory` | `io.codehunters.messaging.factory.OutboxEventFactory` |
| `io.codehunters.messaging.domain.factory.TraceIdSource` | `io.codehunters.messaging.factory.TraceIdSource` |
| `io.codehunters.messaging.application.usecase.SendOutboxMessagesUseCase` | `io.codehunters.messaging.dispatch.SendOutboxMessagesUseCase` |
| `io.codehunters.messaging.infrastructure.metrics.MeteredMessagePublisher` | `io.codehunters.messaging.metrics.MeteredMessagePublisher` |
| `io.codehunters.messaging.config.EventMessagingCoreAutoConfiguration`, `EventMessagingProperties`, `ConditionalOnOutboxTransport`, `OutboxLegacyProperties` | unchanged |

## sdk-event-messaging-jdbc

| 7.x | 8.0 |
|---|---|
| `io.codehunters.messaging.infrastructure.drivenadapters.jdbc.OutboxJdbcAdapter` | `io.codehunters.messaging.jdbc.OutboxJdbcAdapter` |
| `io.codehunters.messaging.infrastructure.drivenadapters.jdbc.OutboxAdapterSettings` | `io.codehunters.messaging.jdbc.OutboxAdapterSettings` |
| `io.codehunters.messaging.infrastructure.drivenadapters.jdbc.OutboxDialect` | `io.codehunters.messaging.jdbc.OutboxDialect` |
| `io.codehunters.messaging.infrastructure.drivenadapters.jdbc.OutboxDialectResolver` | `io.codehunters.messaging.jdbc.OutboxDialectResolver` |
| `io.codehunters.messaging.infrastructure.drivenadapters.jdbc.GenericOutboxDialect` | `io.codehunters.messaging.jdbc.GenericOutboxDialect` |
| `io.codehunters.messaging.infrastructure.drivenadapters.jdbc.PostgresOutboxDialect` | `io.codehunters.messaging.jdbc.PostgresOutboxDialect` |
| `io.codehunters.messaging.infrastructure.drivenadapters.jdbc.SqlServerOutboxDialect` | `io.codehunters.messaging.jdbc.SqlServerOutboxDialect` |
| `io.codehunters.messaging.infrastructure.entrypoint.scheduler.OutboxScheduler` | `io.codehunters.messaging.jdbc.scheduler.OutboxScheduler` |
| `io.codehunters.messaging.infrastructure.entrypoint.scheduler.OutboxPurgeScheduler` | `io.codehunters.messaging.jdbc.scheduler.OutboxPurgeScheduler` |
| `io.codehunters.messaging.infrastructure.entrypoint.scheduler.OutboxPurgeSettings` | `io.codehunters.messaging.jdbc.scheduler.OutboxPurgeSettings` |
| `io.codehunters.messaging.infrastructure.metrics.OutboxFailedGauge` | `io.codehunters.messaging.jdbc.metrics.OutboxFailedGauge` |
| `io.codehunters.messaging.config.EventMessagingJdbcAutoConfiguration` | `io.codehunters.messaging.jdbc.config.EventMessagingJdbcAutoConfiguration` |
| `io.codehunters.messaging.config.OutboxDispatchSchedulerAutoConfiguration` | `io.codehunters.messaging.jdbc.config.OutboxDispatchSchedulerAutoConfiguration` |
| `db/migration/outbox/**` scripts | unchanged |

## sdk-event-messaging-kafka

| 7.x | 8.0 |
|---|---|
| `io.codehunters.messaging.infrastructure.drivenadapters.kafka.KafkaMessagePublisherAdapter` | `io.codehunters.messaging.kafka.KafkaMessagePublisherAdapter` |
| `io.codehunters.messaging.config.EventMessagingKafkaAutoConfiguration` | `io.codehunters.messaging.kafka.config.EventMessagingKafkaAutoConfiguration` |

## sdk-event-messaging-rabbit

| 7.x | 8.0 |
|---|---|
| `io.codehunters.messaging.infrastructure.drivenadapters.rabbitmq.RabbitMessagePublisherAdapter` | `io.codehunters.messaging.rabbit.RabbitMessagePublisherAdapter` |
| `io.codehunters.messaging.config.EventMessagingRabbitAutoConfiguration` | `io.codehunters.messaging.rabbit.config.EventMessagingRabbitAutoConfiguration` |

## Example

```java
// 7.x
import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.domain.factory.OutboxEventFactory;
import io.codehunters.messaging.domain.model.EventTypes;

// 8.0
import io.codehunters.messaging.EventTypes;
import io.codehunters.messaging.OutboxRepositoryPort;
import io.codehunters.messaging.factory.OutboxEventFactory;
```
