package io.codehunters.messaging.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

/**
 * Matches when the given transport publishes the outbox. An explicit {@code
 * codehunters.outbox.publisher.type} always wins; when it is unset, the transport artifact on the
 * classpath decides ({@code sdk-event-messaging-rabbit} or {@code sdk-event-messaging-kafka}). With
 * both artifacts present and the type unset, startup fails naming the key.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnOutboxTransportCondition.class)
public @interface ConditionalOnOutboxTransport {

  /**
   * The transport the annotated configuration provides.
   *
   * @return {@code rabbit} or {@code kafka}
   */
  String value();
}
