package io.codehunters.messaging.config;

import io.codehunters.messaging.application.port.MessagePublisherPort;

/**
 * Wraps the {@link MessagePublisherPort} handed to the outbox dispatch use case, for example to add
 * instrumentation. Every decorator bean is applied, in bean order.
 */
@FunctionalInterface
public interface MessagePublisherDecorator {

  /**
   * Wraps a publisher.
   *
   * @param publisher the publisher to wrap
   * @return the wrapping publisher
   */
  MessagePublisherPort decorate(MessagePublisherPort publisher);
}
