package io.codehunters.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ClaimRequestTest {

  private static final Duration TTL = Duration.ofMinutes(5);

  @Test
  void should_keep_components_when_valid() {
    ClaimRequest request = new ClaimRequest("orders", "orders@1", 10, TTL, 3);

    assertThat(request.batchSize()).isEqualTo(10);
    assertThat(request.claimTtl()).isEqualTo(TTL);
    assertThat(request.maxPerKey()).isEqualTo(3);
  }

  @Test
  void should_reject_request_when_max_per_key_below_one() {
    assertThatThrownBy(() -> new ClaimRequest("orders", "orders@1", 10, TTL, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxPerKey");
  }

  @Test
  void should_reject_request_when_batch_size_below_one() {
    assertThatThrownBy(() -> new ClaimRequest("orders", "orders@1", 0, TTL, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("batchSize");
  }

  @Test
  void should_reject_request_when_claim_ttl_is_zero() {
    assertThatThrownBy(() -> new ClaimRequest("orders", "orders@1", 1, Duration.ZERO, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("claimTtl");
  }

  @Test
  void should_reject_request_when_claim_ttl_is_negative() {
    assertThatThrownBy(() -> new ClaimRequest("orders", "orders@1", 1, Duration.ofSeconds(-1), 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("claimTtl");
  }

  @Test
  void should_reject_request_when_origin_is_null() {
    assertThatThrownBy(() -> new ClaimRequest(null, "orders@1", 1, TTL, 1))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("origin");
  }
}
