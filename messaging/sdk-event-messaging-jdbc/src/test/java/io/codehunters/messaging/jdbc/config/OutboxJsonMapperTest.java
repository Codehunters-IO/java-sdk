package io.codehunters.messaging.jdbc.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.codehunters.messaging.EventMessage;
import io.codehunters.messaging.OutboxEvent;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.factory.EventMessageFactory;
import io.codehunters.messaging.factory.OutboxEventFactory;
import io.codehunters.messaging.factory.TraceIdSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

  private static final EventMessage<Sample> ENVELOPE =
      new EventMessage<>(
          new EventMessage.Header("trace-1", "1.0.0", "1", "orders-ms", AT, null),
          new EventMessage.Body<>(null, "OrderPlaced", "order.placed", SAMPLE));

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
  void should_keep_envelope_field_names_when_application_uses_snake_case() {
    withBootJackson
        .withBean(JsonMapperBuilderCustomizer.class, OutboxJsonMapperTest::snakeCaseNonNull)
        .run(
            context ->
                assertThat(envelopeJson(context))
                    .contains("\"header\":{\"traceId\":\"trace-1\"")
                    .contains("\"schemaVersion\":\"1\"")
                    .contains("\"eventName\":\"OrderPlaced\"")
                    .contains("\"eventType\":\"order.placed\""));
  }

  @Test
  void should_keep_null_envelope_fields_when_application_excludes_nulls() {
    withBootJackson
        .withBean(JsonMapperBuilderCustomizer.class, OutboxJsonMapperTest::snakeCaseNonNull)
        .run(
            context ->
                assertThat(envelopeJson(context))
                    .contains("\"locale\":null")
                    .contains("\"aggregate\":null"));
  }

  @Test
  void should_write_null_event_id_and_idempotency_key_when_envelope_has_the_9_2_shape() {
    withBootJackson
        .withBean(JsonMapperBuilderCustomizer.class, OutboxJsonMapperTest::snakeCaseNonNull)
        .run(
            context ->
                assertThat(envelopeJson(context))
                    .contains("\"eventId\":null")
                    .contains("\"idempotencyKey\":null"));
  }

  @Test
  void should_write_row_id_as_event_id_when_envelope_is_built_by_the_outbox_factory() {
    OutboxEventFactory factory =
        new OutboxEventFactory(
            new EventMessageFactory(
                "orders-ms", TraceIdSource.none(), Clock.fixed(AT, ZoneOffset.UTC)));
    OutboxMessage<EventMessage<Sample>> outbox =
        factory.create(OutboxEvent.to("orders").key("order-7").type("OrderPlaced").payload(SAMPLE));

    withBootJackson
        .withBean(JsonMapperBuilderCustomizer.class, OutboxJsonMapperTest::snakeCaseNonNull)
        .run(
            context ->
                assertThat(
                        context
                            .getBean(OUTBOX_JSON_MAPPER, JsonMapper.class)
                            .writeValueAsString(outbox.message()))
                    .contains("\"eventId\":\"" + outbox.id() + "\""));
  }

  @Test
  void should_apply_application_naming_to_payload_when_envelope_is_pinned() {
    withBootJackson
        .withBean(JsonMapperBuilderCustomizer.class, OutboxJsonMapperTest::snakeCaseNonNull)
        .run(context -> assertThat(envelopeJson(context)).contains("\"order_id\":\"order-7\""));
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

  private static JsonMapperBuilderCustomizer snakeCaseNonNull() {
    return builder ->
        builder
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .changeDefaultPropertyInclusion(
                inclusion -> JsonInclude.Value.construct(Include.NON_NULL, Include.NON_NULL));
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

  private static String envelopeJson(AssertableApplicationContext context) {
    return context.getBean(OUTBOX_JSON_MAPPER, JsonMapper.class).writeValueAsString(ENVELOPE);
  }

  private static String applicationJson(AssertableApplicationContext context) {
    return context.getBean(JsonMapper.class).writeValueAsString(SAMPLE);
  }
}
