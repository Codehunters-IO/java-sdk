package io.codehunters.messaging.rabbit;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ReturnListener;

/**
 * Records whether the broker returned a message published on the channel it listens to. One tracker
 * serves one send on a dedicated channel, so any return it sees belongs to that send.
 */
final class ReturnTracker implements ReturnListener {

  private volatile boolean returned;

  @Override
  public void handleReturn(
      int replyCode,
      String replyText,
      String exchange,
      String routingKey,
      AMQP.BasicProperties properties,
      byte[] body) {
    returned = true;
  }

  boolean isReturned() {
    return returned;
  }
}
