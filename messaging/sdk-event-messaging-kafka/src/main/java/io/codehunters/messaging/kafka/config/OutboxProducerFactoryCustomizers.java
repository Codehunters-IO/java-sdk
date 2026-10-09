package io.codehunters.messaging.kafka.config;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.util.ClassUtils;

/**
 * Applies Boot's {@link DefaultKafkaProducerFactoryCustomizer} beans to the outbox producer
 * factory, in order, so it gets the same listeners (Micrometer metrics, for example) as the
 * application's. {@code spring-boot-kafka} is optional: without it on the classpath nothing is
 * applied, and the customizer type is only loaded once its presence is confirmed.
 */
final class OutboxProducerFactoryCustomizers {

  private static final String CUSTOMIZER =
      "org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer";

  private OutboxProducerFactoryCustomizers() {}

  static void apply(ListableBeanFactory beanFactory, DefaultKafkaProducerFactory<?, ?> factory) {
    if (ClassUtils.isPresent(CUSTOMIZER, OutboxProducerFactoryCustomizers.class.getClassLoader())) {
      BootCustomizers.apply(beanFactory, factory);
    }
  }

  // Separate class so the customizer type is resolved only when spring-boot-kafka is present.
  private static final class BootCustomizers {

    private static void apply(
        ListableBeanFactory beanFactory, DefaultKafkaProducerFactory<?, ?> factory) {
      beanFactory
          .getBeanProvider(DefaultKafkaProducerFactoryCustomizer.class)
          .orderedStream()
          .forEach(customizer -> customizer.customize(factory));
    }
  }
}
