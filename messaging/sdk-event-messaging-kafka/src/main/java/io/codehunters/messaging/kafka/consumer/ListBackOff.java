package io.codehunters.messaging.kafka.consumer;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

/** A {@link BackOff} answering the given delays in order, then {@link BackOffExecution#STOP}. */
final class ListBackOff implements BackOff {

  private final List<Duration> delays;

  ListBackOff(List<Duration> delays) {
    this.delays = List.copyOf(delays);
  }

  @Override
  public BackOffExecution start() {
    Iterator<Duration> next = delays.iterator();
    return () -> next.hasNext() ? next.next().toMillis() : BackOffExecution.STOP;
  }
}
