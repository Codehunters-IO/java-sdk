# sdk-event-messaging-rabbit

RabbitMQ publisher for the transactional outbox, with publisher confirms and mandatory returns.
Model and dispatch semantics are described in
[`sdk-event-messaging-core`](../sdk-event-messaging-core/README.md).

```kotlin
implementation("io.codehunters:sdk-event-messaging-jdbc:6.0.0")
implementation("io.codehunters:sdk-event-messaging-rabbit:6.0.0")
implementation("org.springframework.boot:spring-boot-starter-amqp")
```

`spring-rabbit` is not brought transitively. The publisher is active when `spring-rabbit` is on the
classpath, a `ConnectionFactory` bean exists and RabbitMQ is the outbox transport (this artifact
alone, or `codehunters.outbox.publisher.type=rabbit`).

## Required broker settings

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: simple # or correlated
    publisher-returns: true
```

Without confirms (`simple` or `correlated`) and returns, startup fails with a message naming them.
Either confirm type works: the publisher waits on the channel with `waitForConfirmsOrDie`, which
needs only confirm mode. `correlated` lets the application keep its own `ConfirmCallback` on its
template; the outbox template sets none.

## How it publishes

| Aspect | Behaviour |
|---|---|
| Template | Its own `outboxRabbitTemplate` (`mandatory=true`), built from the application's `ConnectionFactory`. Not a default candidate: the application's `RabbitTemplate`, converter and listeners are untouched |
| Connection | `usePublisherConnection=true`: with Boot's `CachingConnectionFactory` the outbox publishes on its separate publisher connection, which inherits the confirm and returns settings, so a broker that blocks publishers (memory or disk alarm) does not stall the application's consumers. A factory without a publisher connection falls back to its main one |
| Destination | Exchange = outbox `topic`; routing key = outbox `routingKey`, empty when unset |
| Message | Persistent, `application/json`, UTF-8; the payload is sent as stored, no converter |
| Dedupe id | AMQP `messageId` and header `outbox-message-id` = outbox row id |
| Confirm | Each send runs on a dedicated channel and waits for the broker confirm, at most `confirm-timeout` |
| Unroutable | `basic.return` (no queue bound) is logged with the message id, exchange and routing key, and fails the send |
| Trace | When `sdk-tracing` is present, it adds `traceparent` (from the MDC or the envelope's `header.traceId`); this module adds none |

## Failure semantics

All of these raise `MessagePublishException` without broker text, so the row is retried with
backoff and ends `FAILED` after `max-attempts`:

| Situation | Cause |
|---|---|
| Broker nack | Broker could not take the message |
| No confirm within `confirm-timeout` | Broker slow or unreachable |
| Channel closed by the broker | For example a missing exchange |
| `basic.return` | Exchange exists but no queue is bound for the routing key |

The cause is always the publish failure; a failure to clean up the closed channel afterwards is
attached to it as suppressed, never in its place.

A confirm that arrives after the timeout means the message may be in the broker and is sent again:
delivery is at-least-once. Consumers must deduplicate on `outbox-message-id` (or `messageId`).

## Configuration

Keys under `codehunters.outbox`; the rest are in the [core README](../sdk-event-messaging-core/README.md).

| Key | Default | Notes |
|---|---|---|
| `publisher.type` | unset | Set to `rabbit` only when `-kafka` is also on the classpath |
| `publisher.rabbit.confirm-timeout` | `5s` | > 0. Startup fails unless `claim.ttl-ms > batch-size x confirm-timeout` (default 50 x 5 s < 300 s) |

## Extension points

Define a bean named `outboxRabbitTemplate` to replace the template (it must keep `mandatory=true`
and a confirms-enabled factory), or a `MessagePublisherPort` to replace the publisher.

## Migrating from 5.x

| 5.x | 6.0 |
|---|---|
| `sdk-event-messaging:5.x` | `sdk-event-messaging-jdbc` + `sdk-event-messaging-rabbit` 6.0.0 |
| `publisher.type` defaulted to `rabbit` | No default; this artifact alone selects RabbitMQ |
| Unconfirmed send through the SDK-defined `RabbitTemplate` | Confirmed, mandatory send through `outboxRabbitTemplate` |
| SDK defined a global `RabbitTemplate` and `JacksonJsonMessageConverter` | Not defined; declare your own if you injected them |
| SDK attached its own trace post-processor | `sdk-tracing` owns trace propagation |
| No message id | `messageId` and `outbox-message-id` header |
| — | `spring.rabbitmq.publisher-confirm-type=simple` and `publisher-returns=true` required |

Full guide: [MIGRATION-6.0.md](../MIGRATION-6.0.md).
