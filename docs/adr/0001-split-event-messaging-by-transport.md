---
status: accepted
date: 2026-10-05
decision-makers: [andres]
---

# 0001. Split sdk-event-messaging by transport

## Context and Problem Statement

`sdk-event-messaging` ships the outbox domain, the JDBC store with three SQL dialects, the
schedulers, and both the RabbitMQ and Kafka publishers in one jar. Its single auto-configuration
declares beans whose signatures use RabbitMQ, Kafka and JDBC types that are `compileOnly`, so an
application without one of them fails at startup with `NoClassDefFoundError`. ShedLock is forced on
every consumer even when only the publishing types are used.

## Decision Drivers

- A Kafka-only or Rabbit-only service must start without the other broker on the classpath.
- Activating a transport should be a dependency choice, not a property.
- Keep the Java packages stable so consumer imports don't change.

## Considered Options

- Option A — Four artifacts: `-core`, `-jdbc`, `-rabbit`, `-kafka`.
- Option B — One artifact with Gradle feature variants (`rabbit`, `kafka`, `jdbc` capabilities).
- Option C — One artifact, auto-configuration split into nested `@ConditionalOnClass` classes only.

## Decision Outcome

Chosen option: **Option A — four artifacts**, because the dependency a service declares is the
transport it gets, each artifact has one small auto-configuration, and Maven consumers (who ignore
Gradle feature variants) get the same behaviour.

### Consequences

- **Good** — no optional broker types in any auto-configuration signature.
- **Good** — ShedLock and JDBC only arrive with `-jdbc`.
- **Bad** — consumers replace one coordinate with two (`-jdbc` plus a transport).
- **Neutral** — packages `io.codehunters.messaging.*` stay; only artifacts move. Released as 6.0.0.

### Confirmation

- `-core` publishes the Gradle capability `io.codehunters:sdk-event-messaging`, so a build with the
  5.x jar and any 6.x artifact fails resolution instead of loading duplicate classes.
- Each transport artifact has a context test that starts with only that artifact's dependencies.
- ArchUnit in `-core`: `domain` and `application` depend on no Spring, JDBC, AMQP or Kafka types.

## Pros and Cons of the Options

### Option B — feature variants
- Good — one coordinate to publish.
- Bad — invisible to Maven consumers; optional types still sit in one auto-configuration.

### Option C — nested conditional configuration only
- Good — no consumer change.
- Bad — ShedLock and the dialect resolver still reach every consumer; the jar keeps growing.

## More Information

- Migration plan: `docs/plans/2026-10-05-sdk-migration.md`, phase 3.
