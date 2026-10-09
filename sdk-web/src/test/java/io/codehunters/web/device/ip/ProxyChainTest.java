package io.codehunters.web.device.ip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProxyChainTest {

  @Test
  void should_copy_header_lists_so_later_changes_do_not_leak_in() {
    var forwardedFor = new ArrayList<>(List.of("198.51.100.4"));
    var chain = new ProxyChain("10.0.0.2", List.of(), forwardedFor);

    forwardedFor.add("6.6.6.6");

    assertThat(chain.xForwardedFor()).containsExactly("198.51.100.4");
  }

  @Test
  void should_reject_null_header_lists() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ProxyChain("10.0.0.2", null, List.of()))
        .withMessage("header lists must not be null");
  }

  @Test
  void should_drop_null_header_lines() {
    var lines = new ArrayList<String>();
    lines.add(null);
    lines.add("198.51.100.4");

    assertThat(new ProxyChain("10.0.0.2", lines, List.of()).forwarded())
        .containsExactly("198.51.100.4");
  }
}
