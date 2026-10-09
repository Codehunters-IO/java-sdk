# Changelog — sdk-event-messaging-rabbit

## 9.0.0

No changes of its own: versioned with `-core` and `-jdbc` 9.0.0, which support PostgreSQL only and
drop the old `outbox.*` keys. Migration guide: [MIGRATION-9.0.md](../MIGRATION-9.0.md).

## 8.0.0

Migration guide: [MIGRATION-8.0.md](../MIGRATION-8.0.md).

### Breaking
- Packages reorganised by capability: `RabbitMessagePublisherAdapter` moves to
  `io.codehunters.messaging.rabbit` and the auto-configuration to
  `io.codehunters.messaging.rabbit.config`. Only imports change.

## 7.0.0

No changes of its own: versioned with `-core` and `-jdbc` 7.0.0, which charge reclaim attempts only
for rows whose send had started. Migration guide: [MIGRATION-7.0.md](../MIGRATION-7.0.md).

## 6.0.0

First release; the RabbitMQ publisher of `sdk-event-messaging` 5.1.5. Migration guide:
[MIGRATION-6.0.md](../MIGRATION-6.0.md).

### Breaking
- Replaces `sdk-event-messaging` together with `-jdbc`.
- `spring.rabbitmq.publisher-confirm-type=simple` and `spring.rabbitmq.publisher-returns=true` are
  required; startup fails without them.
- No global `RabbitTemplate` or `JacksonJsonMessageConverter` bean, and no trace
  `MessagePostProcessor` (`sdk-tracing` instruments every `RabbitTemplate`).
- `RabbitMessagePublisherAdapter` takes a `RabbitOperations` and a confirm timeout.

### Added
- Sends through a dedicated `outboxRabbitTemplate` (`mandatory=true`, not a default candidate)
  built from the application's `ConnectionFactory`.
- Each send waits for the broker confirm, at most `codehunters.outbox.publisher.rabbit.confirm-timeout`
  (default `5s`).
- AMQP `messageId` and `outbox-message-id` header set to the outbox row id.
- Selected automatically when it is the only transport artifact present.
- Accepts `correlated` as well as `simple` publisher confirms.
- The outbox template publishes on the connection factory's publisher connection
  (`usePublisherConnection=true`), so blocked publishing never stalls consumers.

### Fixed
- A nack, a missing exchange or an unroutable message (`basic.return`) was marked `PUBLISHED`; it
  now raises `MessagePublishException` and the row is retried.
- When the broker had closed the channel, a failure to remove the return listener replaced the
  publish failure as the cause; the publish failure is kept, with the removal failure suppressed,
  and a removal failure after a confirmed send no longer fails the send.
