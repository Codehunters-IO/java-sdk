package io.codehunters.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxDispatchStore;
import io.codehunters.messaging.OutboxWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** An outbox that is written to but has no dispatch is reported at startup. */
@ExtendWith(OutputCaptureExtension.class)
class OutboxDispatchCheckTest {

  private static final String NEVER_PUBLISHED = "Outbox messages are written but never published";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(EventMessagingCoreAutoConfiguration.class))
          .withPropertyValues("spring.application.name=test-app");

  private final ApplicationContextRunner writing =
      runner.withBean(OutboxWriter.class, () -> mock(OutboxWriter.class));

  @Test
  void should_warn_naming_missing_store_and_publisher_when_only_writer_exists(
      CapturedOutput output) {
    writing.run(context -> assertThat(context).hasNotFailed());

    assertThat(output)
        .contains(NEVER_PUBLISHED)
        .contains("no OutboxDispatchStore")
        .contains("no MessagePublisherPort");
  }

  @Test
  void should_warn_naming_only_missing_publisher_when_store_exists(CapturedOutput output) {
    writing
        .withBean(OutboxDispatchStore.class, () -> mock(OutboxDispatchStore.class))
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).contains("no MessagePublisherPort").doesNotContain("no OutboxDispatchStore");
  }

  @Test
  void should_fail_startup_when_publisher_type_is_set_but_nothing_dispatches() {
    writing
        .withPropertyValues("codehunters.outbox.publisher.type=kafka")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .hasMessageContaining(NEVER_PUBLISHED)
                    .hasMessageContaining("codehunters.outbox.publisher.type"));
  }

  @Test
  void should_stay_quiet_when_written_messages_are_dispatched(CapturedOutput output) {
    writing
        .withBean(OutboxDispatchStore.class, () -> mock(OutboxDispatchStore.class))
        .withBean(MessagePublisherPort.class, () -> mock(MessagePublisherPort.class))
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).doesNotContain(NEVER_PUBLISHED);
  }

  @Test
  void should_stay_quiet_when_nothing_writes_to_the_outbox(CapturedOutput output) {
    runner.run(context -> assertThat(context).hasNotFailed());

    assertThat(output).doesNotContain(NEVER_PUBLISHED);
  }
}
