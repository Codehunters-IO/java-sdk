package io.codehunters.web.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.codehunters.web.device.ip.IpLiterals;
import io.codehunters.web.device.useragent.UserAgentInfo;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClientContextTest {

  @Test
  void should_expose_the_ip_as_text_when_it_is_known() {
    var context =
        new ClientContext(IpLiterals.parse("198.51.100.4"), UserAgentInfo.UNKNOWN, "Mozilla/5.0");

    assertThat(context.ipAddress()).contains("198.51.100.4");
  }

  @Test
  void should_have_no_ip_text_when_the_ip_is_unknown() {
    var context = new ClientContext(Optional.empty(), UserAgentInfo.UNKNOWN, "");

    assertThat(context.ipAddress()).isEmpty();
  }

  @Test
  void should_reject_null_components() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ClientContext(Optional.empty(), null, ""))
        .withMessage("client context components must not be null");
  }
}
