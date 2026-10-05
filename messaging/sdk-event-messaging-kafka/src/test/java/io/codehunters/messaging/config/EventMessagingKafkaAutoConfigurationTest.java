package io.codehunters.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.application.port.SendOutboxMessagesPort;
import io.codehunters.messaging.infrastructure.drivenadapters.kafka.KafkaMessagePublisherAdapter;
import io.codehunters.messaging.infrastructure.entrypoint.scheduler.OutboxScheduler;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

class EventMessagingKafkaAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  EventMessagingKafkaAutoConfiguration.class,
                  EventMessagingJdbcAutoConfiguration.class,
                  EventMessagingCoreAutoConfiguration.class,
                  OutboxDispatchSchedulerAutoConfiguration.class))
          .withPropertyValues("spring.application.name=test-app");

  @Test
  void should_select_kafka_publisher_when_publisher_type_is_kafka() {
    contextRunner
        .withClassLoader(new FilteredClassLoader("org.springframework.amqp"))
        .withPropertyValues("codehunters.outbox.publisher.type=kafka")
        .withBean(DataSource.class, this::createDataSource)
        .withBean(ProducerFactory.class, EventMessagingKafkaAutoConfigurationTest::producers)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context)
                  .getBean(MessagePublisherPort.class)
                  .isInstanceOf(KafkaMessagePublisherAdapter.class);
              assertThat(context).hasSingleBean(SendOutboxMessagesPort.class);
              assertThat(context).hasSingleBean(OutboxScheduler.class);
            });
  }

  @Test
  void should_select_kafka_publisher_when_type_unset_and_only_kafka_artifact_present() {
    contextRunner
        .withBean(ProducerFactory.class, EventMessagingKafkaAutoConfigurationTest::producers)
        .run(
            context ->
                assertThat(context)
                    .getBean(MessagePublisherPort.class)
                    .isInstanceOf(KafkaMessagePublisherAdapter.class));
  }

  @Test
  void should_build_idempotent_string_outbox_template_when_application_producer_uses_json() {
    contextRunner
        .withBean(ProducerFactory.class, EventMessagingKafkaAutoConfigurationTest::producers)
        .run(
            context ->
                assertThat(
                        context
                            .getBean("outboxKafkaTemplate", KafkaTemplate.class)
                            .getProducerFactory()
                            .getConfigurationProperties())
                    .containsEntry(
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                    .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
                    .containsEntry(ProducerConfig.ACKS_CONFIG, "all"));
  }

  @Test
  void should_keep_outbox_template_out_of_type_injection_when_built() {
    contextRunner
        .withBean(ProducerFactory.class, EventMessagingKafkaAutoConfigurationTest::producers)
        .run(
            context ->
                assertThat(
                        ((AbstractBeanDefinition)
                                context.getBeanFactory().getBeanDefinition("outboxKafkaTemplate"))
                            .isDefaultCandidate())
                    .isFalse());
  }

  @Test
  void should_not_create_kafka_publisher_when_producer_factory_missing() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(MessagePublisherPort.class));
  }

  @Test
  void should_not_create_kafka_publisher_when_publisher_type_is_rabbit() {
    contextRunner
        .withPropertyValues("codehunters.outbox.publisher.type=rabbit")
        .withBean(ProducerFactory.class, EventMessagingKafkaAutoConfigurationTest::producers)
        .run(context -> assertThat(context).doesNotHaveBean(MessagePublisherPort.class));
  }

  @Test
  void should_fail_startup_naming_budget_when_type_unset_and_kafka_claim_budget_exceeded() {
    contextRunner
        .withPropertyValues("codehunters.outbox.claim.ttl-ms=1000")
        .withBean(ProducerFactory.class, EventMessagingKafkaAutoConfigurationTest::producers)
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("codehunters.outbox.publisher.kafka.send-timeout"));
  }

  @Test
  void should_fail_startup_naming_setting_when_application_producer_config_invalid() {
    contextRunner
        .withBean(
            ProducerFactory.class,
            () -> producersWith(Map.of(ProducerConfig.COMPRESSION_TYPE_CONFIG, "bogus")))
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining(ProducerConfig.COMPRESSION_TYPE_CONFIG));
  }

  @Test
  void should_start_when_application_producer_config_conflicts_with_idempotence() {
    contextRunner
        .withBean(
            ProducerFactory.class,
            () ->
                producersWith(
                    Map.of(
                        ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
                        10,
                        ProducerConfig.RETRIES_CONFIG,
                        0)))
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void should_apply_boot_producer_factory_customizers_when_building_outbox_factory() {
    ProducerFactory.Listener<Object, Object> listener = new ProducerFactory.Listener<>() {};

    contextRunner
        .withBean(ProducerFactory.class, EventMessagingKafkaAutoConfigurationTest::producers)
        .withBean(DefaultKafkaProducerFactoryCustomizer.class, () -> addListener(listener))
        .run(
            context -> assertThat(outboxFactory(context).getListeners()).containsExactly(listener));
  }

  @Test
  void should_keep_string_serializers_when_customizer_sets_application_serializer() {
    contextRunner
        .withBean(ProducerFactory.class, EventMessagingKafkaAutoConfigurationTest::producers)
        .withBean(
            DefaultKafkaProducerFactoryCustomizer.class,
            EventMessagingKafkaAutoConfigurationTest::jsonValueSerializer)
        .run(
            context ->
                assertThat(outboxFactory(context).getValueSerializerSupplier().get())
                    .isInstanceOf(StringSerializer.class));
  }

  @Test
  void should_give_outbox_producer_its_own_client_id_when_application_sets_none() {
    contextRunner
        .withBean(ProducerFactory.class, EventMessagingKafkaAutoConfigurationTest::producers)
        .run(
            context ->
                assertThat(outboxFactory(context).getConfigurationProperties())
                    .containsEntry(ProducerConfig.CLIENT_ID_CONFIG, "outbox-test-app"));
  }

  @Test
  void should_fail_startup_asking_for_primary_when_several_producer_factories_present() {
    contextRunner
        .withBean("ordersProducers", ProducerFactory.class, () -> producersAt("orders:9092"))
        .withBean("auditProducers", ProducerFactory.class, () -> producersAt("audit:9092"))
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("ProducerFactory")
                    .hasMessageContaining("@Primary"));
  }

  @Test
  void should_derive_outbox_factory_from_primary_when_several_producer_factories_present() {
    contextRunner
        .withBean("ordersProducers", ProducerFactory.class, () -> producersAt("orders:9092"))
        .withBean(
            "auditProducers",
            ProducerFactory.class,
            () -> producersAt("audit:9092"),
            definition -> definition.setPrimary(true))
        .run(
            context ->
                assertThat(outboxFactory(context).getConfigurationProperties())
                    .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "audit:9092"));
  }

  @Test
  void should_fail_startup_naming_override_when_producer_factory_hides_its_configuration() {
    contextRunner
        .withBean(ProducerFactory.class, () -> mock(ProducerFactory.class, CALLS_REAL_METHODS))
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("outboxKafkaProducerFactory"));
  }

  @SuppressWarnings("unchecked")
  private static DefaultKafkaProducerFactory<Object, Object> outboxFactory(
      AssertableApplicationContext context) {
    return context.getBean("outboxKafkaProducerFactory", DefaultKafkaProducerFactory.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static DefaultKafkaProducerFactoryCustomizer addListener(
      ProducerFactory.Listener<Object, Object> listener) {
    return factory -> ((DefaultKafkaProducerFactory) factory).addListener(listener);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static DefaultKafkaProducerFactoryCustomizer jsonValueSerializer() {
    return factory ->
        ((DefaultKafkaProducerFactory) factory).setValueSerializer(new JacksonJsonSerializer<>());
  }

  private static ProducerFactory<Object, Object> producersAt(String bootstrapServers) {
    return producersWith(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
  }

  @SuppressWarnings("unchecked")
  private static ProducerFactory<Object, Object> producersWith(Map<String, Object> settings) {
    Map<String, Object> config =
        new HashMap<>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092"));
    config.putAll(settings);
    ProducerFactory<Object, Object> producerFactory = mock(ProducerFactory.class);
    when(producerFactory.getConfigurationProperties()).thenReturn(config);
    return producerFactory;
  }

  @SuppressWarnings("unchecked")
  private static ProducerFactory<Object, Object> producers() {
    ProducerFactory<Object, Object> producerFactory = mock(ProducerFactory.class);
    when(producerFactory.getConfigurationProperties())
        .thenReturn(
            Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9092",
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JacksonJsonSerializer.class));
    return producerFactory;
  }

  private DataSource createDataSource() {
    return new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build();
  }
}
