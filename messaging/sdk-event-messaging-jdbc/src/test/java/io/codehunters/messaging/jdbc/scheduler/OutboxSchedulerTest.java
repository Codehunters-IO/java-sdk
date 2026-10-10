package io.codehunters.messaging.jdbc.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.codehunters.messaging.SendOutboxMessagesPort;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class OutboxSchedulerTest {

  @Mock private SendOutboxMessagesPort sendOutboxMessagesPort;

  private final AtomicInteger dispatchRequests = new AtomicInteger();
  private OutboxScheduler scheduler;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    scheduler = new OutboxScheduler(sendOutboxMessagesPort, dispatchRequests::incrementAndGet);
    logAppender = new ListAppender<>();
    logAppender.start();
    schedulerLogger().addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    schedulerLogger().detachAppender(logAppender);
  }

  private static Logger schedulerLogger() {
    return (Logger) LoggerFactory.getLogger(OutboxScheduler.class);
  }

  @Test
  void should_delegate_to_send_outbox_messages_port() {
    scheduler.run();

    verify(sendOutboxMessagesPort).execute();
  }

  @Test
  void should_request_dispatch_outside_the_lock_when_claim_came_back_full() {
    when(sendOutboxMessagesPort.execute()).thenReturn(true);

    scheduler.run();

    assertThat(dispatchRequests).hasValue(1);
  }

  @Test
  void should_not_request_dispatch_when_claim_came_back_short() {
    when(sendOutboxMessagesPort.execute()).thenReturn(false);

    scheduler.run();

    assertThat(dispatchRequests).hasValue(0);
  }

  @Test
  void should_not_request_dispatch_when_built_without_trigger() {
    when(sendOutboxMessagesPort.execute()).thenReturn(true);

    assertDoesNotThrow(() -> new OutboxScheduler(sendOutboxMessagesPort).run());
  }

  @Test
  void should_not_propagate_exception_from_use_case() {
    doThrow(new RuntimeException("db error")).when(sendOutboxMessagesPort).execute();

    assertDoesNotThrow(() -> scheduler.run());
  }

  @Test
  void should_log_exception_class_name_only_when_use_case_fails() {
    doThrow(new RuntimeException("PII-SENTINEL")).when(sendOutboxMessagesPort).execute();

    scheduler.run();

    assertThat(logAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(line -> line.contains("java.lang.RuntimeException"))
        .noneMatch(line -> line.contains("PII-SENTINEL"));
    assertThat(logAppender.list).allMatch(event -> event.getThrowableProxy() == null);
  }
}
