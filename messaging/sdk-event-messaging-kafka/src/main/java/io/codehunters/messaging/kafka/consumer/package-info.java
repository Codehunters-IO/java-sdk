/**
 * Consumer side of the event envelope: {@link
 * io.codehunters.messaging.kafka.consumer.EnvelopeReader} reads an {@link
 * io.codehunters.messaging.EventMessage} from a Kafka record as a {@link
 * io.codehunters.messaging.kafka.consumer.ReceivedEnvelope}, which exposes the ids consumers dedupe
 * on ({@code eventId}, {@code idempotencyKey}, {@code outbox-message-id}) and the delivery attempt.
 */
package io.codehunters.messaging.kafka.consumer;
