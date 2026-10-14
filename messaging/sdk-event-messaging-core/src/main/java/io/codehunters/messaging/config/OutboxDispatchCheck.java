package io.codehunters.messaging.config;

import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxDispatchStore;
import io.codehunters.messaging.OutboxWriter;
import io.codehunters.messaging.SendOutboxMessagesPort;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;

/**
 * Catches an outbox that is written to but never dispatched. The dispatch port backs off without a
 * word when its store or publisher is missing: an application {@link OutboxWriter} makes the JDBC
 * adapter back off, and a transport whose broker beans are absent registers no publisher. Rows are
 * then committed and never sent. Fails startup when {@code publisher.type} names a transport, since
 * dispatch was clearly intended; warns otherwise, as another service may dispatch the table.
 */
final class OutboxDispatchCheck implements SmartInitializingSingleton {

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatchCheck.class);
  private static final String TYPE_KEY = "codehunters.outbox.publisher.type";

  private final ListableBeanFactory beanFactory;
  private final Environment environment;

  OutboxDispatchCheck(ListableBeanFactory beanFactory, Environment environment) {
    this.beanFactory = beanFactory;
    this.environment = environment;
  }

  @Override
  public void afterSingletonsInstantiated() {
    if (!has(OutboxWriter.class) || has(SendOutboxMessagesPort.class)) {
      return;
    }
    String message =
        "Outbox messages are written but never published: no SendOutboxMessagesPort, because "
            + String.join(" and ", missing())
            + ".";
    if (environment.containsProperty(TYPE_KEY)) {
      throw new IllegalStateException(
          message + " " + TYPE_KEY + " is set, so dispatch is required");
    }
    log.warn("{} Ignore this if another service dispatches the outbox table.", message);
  }

  private List<String> missing() {
    List<String> missing = new ArrayList<>();
    if (!has(OutboxDispatchStore.class)) {
      missing.add(
          "there is no OutboxDispatchStore (an application OutboxWriter makes the JDBC adapter"
              + " back off)");
    }
    if (!has(MessagePublisherPort.class)) {
      missing.add(
          "there is no MessagePublisherPort (add sdk-event-messaging-rabbit or -kafka and its"
              + " broker connection)");
    }
    return missing;
  }

  private boolean has(Class<?> type) {
    return beanFactory.getBeanNamesForType(type, true, false).length > 0;
  }
}
