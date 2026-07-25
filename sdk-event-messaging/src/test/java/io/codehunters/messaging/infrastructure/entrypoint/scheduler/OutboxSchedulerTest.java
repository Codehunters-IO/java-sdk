package io.codehunters.messaging.infrastructure.entrypoint.scheduler;

import static org.mockito.Mockito.*;

import io.codehunters.messaging.application.port.SendOutboxMessagesPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxSchedulerTest {

  @Mock private SendOutboxMessagesPort sendOutboxMessagesPort;
  @InjectMocks private OutboxScheduler scheduler;

  @Test
  void should_delegate_to_send_outbox_messages_port() {
    scheduler.processOutboxMessages();

    verify(sendOutboxMessagesPort).execute();
  }

  @Test
  void should_not_propagate_exception_from_use_case() {
    doThrow(new RuntimeException("db error")).when(sendOutboxMessagesPort).execute();

    org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> scheduler.processOutboxMessages());
  }
}
