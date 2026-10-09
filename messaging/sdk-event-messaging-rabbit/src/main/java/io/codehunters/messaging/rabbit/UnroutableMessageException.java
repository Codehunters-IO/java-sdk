package io.codehunters.messaging.rabbit;

import org.springframework.amqp.AmqpException;

/** Cause of a publish failure when the broker returned the message as unroutable. */
final class UnroutableMessageException extends AmqpException {

  UnroutableMessageException() {
    super("Message returned by the broker: no queue bound for its routing key");
  }
}
