package io.codehunters.fileshare.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.fileshare.FileShareException;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PresignedUrlPolicyTest {

  private final PresignedUrlPolicy anyHost = new PresignedUrlPolicy(List.of());
  private final PresignedUrlPolicy restricted =
      new PresignedUrlPolicy(List.of("files.example.com", "*.s3.amazonaws.com"));

  @Test
  void should_accept_any_https_host_when_no_hosts_configured() {
    assertThat(anyHost.requireAllowed("https://anything.example/key?sig=1"))
        .isEqualTo(URI.create("https://anything.example/key?sig=1"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://10.0.3.7:8443/admin", "https://anything.example:80/key"})
  void should_reject_non_default_port_when_any_host_is_allowed(String url) {
    assertThatThrownBy(() -> anyHost.requireAllowed(url))
        .isInstanceOf(FileShareException.class)
        .hasMessageContaining("port");
  }

  @Test
  void should_accept_exact_host_ignoring_case() {
    assertThat(restricted.requireAllowed("https://FILES.example.com/key")).isNotNull();
  }

  @Test
  void should_accept_subdomain_of_wildcard_host() {
    assertThat(restricted.requireAllowed("https://bucket.s3.amazonaws.com/key")).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://s3.amazonaws.com/key",
        "https://evil.com/key",
        "https://files.example.com.evil.com/key",
        "https://evils3.amazonaws.com/key"
      })
  void should_reject_host_when_not_allowed(String url) {
    assertThatThrownBy(() -> restricted.requireAllowed(url))
        .isInstanceOf(FileShareException.class)
        .hasMessageContaining("host");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://files.example.com@evil.com/key",
        "https://files.example.com./key",
        "https://169.254.169.254/key",
        "https://files.example.com:8443/key",
        "https://bucket.s3.amazonaws.com:80/key"
      })
  void should_reject_url_when_restricted_and_host_or_port_differs(String url) {
    assertThatThrownBy(() -> restricted.requireAllowed(url)).isInstanceOf(FileShareException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"HTTPS://files.example.com/key", "https://files.example.com:443/key"})
  void should_accept_url_when_scheme_case_or_default_port_differs(String url) {
    assertThat(restricted.requireAllowed(url)).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"http://files.example.com/key", "ftp://files.example.com/key"})
  void should_reject_url_when_not_https(String url) {
    assertThatThrownBy(() -> anyHost.requireAllowed(url))
        .isInstanceOf(FileShareException.class)
        .hasMessageContaining("https");
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://", "not a url", "https:///key", "/relative/key"})
  void should_reject_url_when_malformed(String url) {
    assertThatThrownBy(() -> anyHost.requireAllowed(url)).isInstanceOf(FileShareException.class);
  }

  @Test
  void should_reject_missing_url() {
    assertThatThrownBy(() -> anyHost.requireAllowed(null))
        .isInstanceOf(FileShareException.class)
        .hasMessageContaining("presignedUrl");
  }

  @Test
  void should_not_echo_signature_when_rejecting() {
    assertThatThrownBy(() -> restricted.requireAllowed("https://evil.com/key?X-Amz-Signature=abc"))
        .hasMessageNotContaining("abc");
  }
}
