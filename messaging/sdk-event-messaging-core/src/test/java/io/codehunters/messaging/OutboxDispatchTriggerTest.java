package io.codehunters.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

class OutboxDispatchTriggerTest {

  @Test
  void should_do_nothing_when_none_trigger_is_requested() {
    assertThatNoException().isThrownBy(() -> OutboxDispatchTrigger.none().requestDispatch());
  }

  @Test
  void should_return_one_shared_instance_when_none_requested_twice() {
    assertThat(OutboxDispatchTrigger.none()).isSameAs(OutboxDispatchTrigger.none());
  }
}
