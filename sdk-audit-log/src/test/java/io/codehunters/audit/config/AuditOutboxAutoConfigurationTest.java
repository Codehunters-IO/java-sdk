package io.codehunters.audit.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.audit.AuditEventPublisherPort;
import io.codehunters.audit.internal.aspect.AuditAspect;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.OutboxWriter;
import io.codehunters.messaging.factory.EventMessageFactory;
import io.codehunters.messaging.factory.OutboxEventFactory;
import io.codehunters.messaging.factory.TraceIdSource;
import java.time.Clock;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

class AuditOutboxAutoConfigurationTest {

  private static final String SINK_OUTBOX = "codehunters.audit.sink=outbox";
  private static final String TOPIC = "codehunters.audit.outbox.topic=ocr.audit";
  private static final String OUTBOX_PUBLISHER = "outboxAuditEventPublisher";
  private static final String JDBC_PUBLISHER = "auditEventPublisher";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  AopAutoConfiguration.class,
                  AuditOutboxAutoConfiguration.class,
                  AuditJdbcAutoConfiguration.class,
                  AuditAutoConfiguration.class))
          .withBean(
              "dataSource",
              DataSource.class,
              () -> new DriverManagerDataSource("jdbc:h2:mem:audit-outbox-sink"));

  private final ApplicationContextRunner withMessaging =
      runner
          .withBean(OutboxWriter.class, Discarding::new)
          .withBean(
              OutboxEventFactory.class,
              () ->
                  new OutboxEventFactory(
                      new EventMessageFactory(
                          "audit-test", TraceIdSource.none(), Clock.systemUTC())))
          .withUserConfiguration(OutboxJsonMapperConfig.class);

  @Test
  void should_register_outbox_publisher_when_sink_is_outbox() {
    withMessaging
        .withPropertyValues(SINK_OUTBOX, TOPIC)
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(AuditEventPublisherPort.class);
              assertThat(ctx).hasBean(OUTBOX_PUBLISHER);
              assertThat(ctx)
                  .doesNotHaveBean(JDBC_PUBLISHER)
                  .doesNotHaveBean("auditTaskExecutor")
                  .doesNotHaveBean("auditJsonMapper");
            });
  }

  @Test
  void should_accept_sink_value_in_any_case() {
    withMessaging
        .withPropertyValues("codehunters.audit.sink=OUTBOX", TOPIC)
        .run(ctx -> assertThat(ctx).hasBean(OUTBOX_PUBLISHER));
  }

  @Test
  void should_register_outbox_publisher_when_outbox_json_mapper_is_missing() {
    runner
        .withBean(OutboxWriter.class, Discarding::new)
        .withBean(
            OutboxEventFactory.class,
            () ->
                new OutboxEventFactory(
                    new EventMessageFactory("audit-test", TraceIdSource.none(), Clock.systemUTC())))
        .withPropertyValues(SINK_OUTBOX, TOPIC)
        .run(ctx -> assertThat(ctx).hasBean(OUTBOX_PUBLISHER));
  }

  @Test
  void should_keep_jdbc_publisher_when_sink_is_unset() {
    withMessaging.run(
        ctx -> {
          assertThat(ctx).hasBean(JDBC_PUBLISHER);
          assertThat(ctx).hasBean("auditTaskExecutor").hasBean("auditJsonMapper");
          assertThat(ctx).doesNotHaveBean(OUTBOX_PUBLISHER);
        });
  }

  @Test
  void should_keep_jdbc_publisher_when_sink_is_jdbc() {
    withMessaging
        .withPropertyValues("codehunters.audit.sink=jdbc", TOPIC)
        .run(
            ctx -> {
              assertThat(ctx).hasBean(JDBC_PUBLISHER);
              assertThat(ctx).doesNotHaveBean(OUTBOX_PUBLISHER);
            });
  }

  @Test
  void should_fail_startup_when_sink_is_unknown() {
    withMessaging
        .withPropertyValues("codehunters.audit.sink=kafka")
        .run(
            ctx ->
                assertThat(ctx)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("codehunters.audit.sink"));
  }

  @Test
  void should_fail_startup_when_outbox_topic_is_blank() {
    withMessaging
        .withPropertyValues(SINK_OUTBOX)
        .run(
            ctx ->
                assertThat(ctx)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("codehunters.audit.outbox.topic"));
  }

  @Test
  void should_fail_with_actionable_message_when_messaging_is_absent() {
    runner
        .withClassLoader(new FilteredClassLoader("io.codehunters.messaging"))
        .withPropertyValues(SINK_OUTBOX, TOPIC)
        .run(
            ctx ->
                assertThat(ctx)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining(SINK_OUTBOX)
                    .hasMessageContaining("sdk-event-messaging-jdbc"));
  }

  @Test
  void should_fail_with_actionable_message_when_outbox_writer_bean_is_missing() {
    runner
        .withPropertyValues(SINK_OUTBOX, TOPIC)
        .run(
            ctx ->
                assertThat(ctx)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining(SINK_OUTBOX)
                    .hasMessageContaining("codehunters.outbox.enabled"));
  }

  @Test
  void should_register_audit_aspect_when_sink_is_outbox() {
    withMessaging
        .withPropertyValues(SINK_OUTBOX, TOPIC)
        .run(ctx -> assertThat(ctx).hasSingleBean(AuditAspect.class).hasBean("auditAspect"));
  }

  @Test
  void should_not_register_outbox_publisher_when_audit_is_disabled() {
    withMessaging
        .withPropertyValues(SINK_OUTBOX, TOPIC, "codehunters.audit.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(AuditEventPublisherPort.class));
  }

  @Configuration(proxyBeanMethods = false)
  static class OutboxJsonMapperConfig {
    @Bean(name = "outboxJsonMapper", defaultCandidate = false)
    JsonMapper outboxJsonMapper() {
      return JsonMapper.builder().build();
    }
  }

  private static final class Discarding implements OutboxWriter {
    @Override
    public <T> void save(OutboxMessage<T> outboxMessage) {}
  }
}
