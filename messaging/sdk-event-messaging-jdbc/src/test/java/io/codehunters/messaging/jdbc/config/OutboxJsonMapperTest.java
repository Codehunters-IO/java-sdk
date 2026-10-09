package io.codehunters.messaging.jdbc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The dedicated {@code outboxJsonMapper} starts from the application's Jackson setup (Boot's {@link
 * JsonMapper.Builder} with every {@link JsonMapperBuilderCustomizer}) but always writes compact
 * JSON with ISO-8601 instants, the wire format consumers parse.
 */
class OutboxJsonMapperTest {

  private static final String OUTBOX_JSON_MAPPER = "outboxJsonMapper";
  private static final Instant AT = Instant.parse("2026-10-09T10:15:30Z");
  private static final Sample SAMPLE = new Sample("order-7", AT);

  record Sample(String orderId, Instant placedAt) {}

  private final ApplicationContextRunner withBootJackson =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  EventMessagingJdbcAutoConfiguration.class, JacksonAutoConfiguration.class));

  @Test
  void should_apply_application_customizer_when_boot_jackson_is_configured() {
    withBootJackson
        .withBean(JsonMapperBuilderCustomizer.class, OutboxJsonMapperTest::snakeCase)
        .run(context -> assertThat(outboxJson(context)).contains("\"order_id\":\"order-7\""));
  }

  @Test
  void should_write_compact_json_when_application_enables_indentation() {
    withBootJackson
        .withBean(JsonMapperBuilderCustomizer.class, OutboxJsonMapperTest::indented)
        .run(context -> assertThat(outboxJson(context)).doesNotContain("\n"));
  }

  @Test
  void should_keep_application_indentation_when_outbox_mapper_is_compact() {
    withBootJackson
        .withBean(JsonMapperBuilderCustomizer.class, OutboxJsonMapperTest::indented)
        .run(context -> assertThat(applicationJson(context)).contains("\n"));
  }

  @Test
  void should_write_iso_instants_when_application_enables_timestamps() {
    withBootJackson
        .withBean(JsonMapperBuilderCustomizer.class, OutboxJsonMapperTest::timestamps)
        .run(
            context ->
                assertThat(outboxJson(context)).contains("\"placedAt\":\"2026-10-09T10:15:30Z\""));
  }

  @Test
  void should_keep_application_timestamps_when_outbox_mapper_writes_iso_instants() {
    withBootJackson
        .withBean(JsonMapperBuilderCustomizer.class, OutboxJsonMapperTest::timestamps)
        .run(context -> assertThat(applicationJson(context)).doesNotContain("2026-10-09T"));
  }

  @Test
  void should_leave_application_builder_untouched_when_it_is_a_singleton() {
    JsonMapper.Builder applicationBuilder =
        JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT);

    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(EventMessagingJdbcAutoConfiguration.class))
        .withBean(JsonMapper.Builder.class, () -> applicationBuilder)
        .run(
            context -> {
              assertThat(outboxJson(context)).doesNotContain("\n");
              assertThat(applicationBuilder.build().writeValueAsString(SAMPLE)).contains("\n");
            });
  }

  @Test
  void should_write_compact_iso_json_when_boot_jackson_is_absent() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(EventMessagingJdbcAutoConfiguration.class))
        .run(
            context ->
                assertThat(outboxJson(context))
                    .isEqualTo("{\"orderId\":\"order-7\",\"placedAt\":\"2026-10-09T10:15:30Z\"}"));
  }

  @Test
  void should_keep_application_outbox_json_mapper_when_one_is_defined() {
    JsonMapper applicationOutboxMapper = JsonMapper.builder().build();

    withBootJackson
        .withBean(OUTBOX_JSON_MAPPER, JsonMapper.class, () -> applicationOutboxMapper)
        .run(
            context ->
                assertThat(context.getBean(OUTBOX_JSON_MAPPER)).isSameAs(applicationOutboxMapper));
  }

  @Test
  void should_order_after_boot_jackson_when_both_auto_configurations_load() {
    AutoConfiguration ordering =
        EventMessagingJdbcAutoConfiguration.class.getAnnotation(AutoConfiguration.class);

    assertThat(ordering.afterName()).contains(JacksonAutoConfiguration.class.getName());
  }

  private static JsonMapperBuilderCustomizer snakeCase() {
    return builder -> builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
  }

  private static JsonMapperBuilderCustomizer indented() {
    return builder -> builder.enable(SerializationFeature.INDENT_OUTPUT);
  }

  private static JsonMapperBuilderCustomizer timestamps() {
    return builder -> builder.enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  private static String outboxJson(AssertableApplicationContext context) {
    return context.getBean(OUTBOX_JSON_MAPPER, JsonMapper.class).writeValueAsString(SAMPLE);
  }

  private static String applicationJson(AssertableApplicationContext context) {
    return context.getBean(JsonMapper.class).writeValueAsString(SAMPLE);
  }
}
