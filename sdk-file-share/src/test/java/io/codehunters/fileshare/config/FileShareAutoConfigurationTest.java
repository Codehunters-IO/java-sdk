package io.codehunters.fileshare.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.fileshare.application.FileShareClient;
import io.codehunters.fileshare.domain.exception.FileShareException;
import io.codehunters.fileshare.domain.exception.FileShareUnavailableException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class FileShareAutoConfigurationTest {

  private static final UUID FILE_ID = UUID.fromString(FakeFileShareServer.FILE_ID);
  private static final long CONTENT_SIZE =
      (long) FakeFileShareServer.CONTENT_CHUNKS * FakeFileShareServer.CONTENT_CHUNK_SIZE;

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(FileShareAutoConfiguration.class));

  private FakeFileShareServer server;

  @BeforeEach
  void startServer() throws IOException {
    server = FakeFileShareServer.start();
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  @Test
  void should_create_client_when_base_url_configured() {
    runner
        .withPropertyValues("codehunters.file-share.base-url=" + server.url())
        .run(
            context ->
                assertThat(context.getBean(FileShareClient.class).getMetadata("REF-1", FILE_ID))
                    .extracting("filename")
                    .isEqualTo("f.pdf"));
  }

  @Test
  void should_fail_when_base_url_missing() {
    runner.run(
        context ->
            assertThat(context)
                .hasFailed()
                .getFailure()
                .hasMessageContaining("codehunters.file-share.base-url"));
  }

  @Test
  void should_fail_when_base_url_is_not_absolute_http() {
    runner
        .withPropertyValues("codehunters.file-share.base-url=localhost:9000")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasMessageContaining("codehunters.file-share.base-url"));
  }

  @Test
  void should_fail_when_base_url_is_not_a_uri() {
    runner
        .withPropertyValues("codehunters.file-share.base-url=http://files host")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasMessageContaining("codehunters.file-share.base-url"));
  }

  @Test
  void should_create_nothing_when_disabled() {
    runner
        .withPropertyValues("codehunters.file-share.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(FileShareClient.class));
  }

  @Test
  void should_keep_application_rest_client_as_the_only_autowire_candidate() {
    RestClient appClient = RestClient.create();
    runner
        .withPropertyValues("codehunters.file-share.base-url=" + server.url())
        .withBean("appClient", RestClient.class, () -> appClient)
        .withBean(RestClientConsumer.class)
        .run(
            context ->
                assertThat(context.getBean(RestClientConsumer.class).client()).isSameAs(appClient));
  }

  @Test
  void should_build_client_from_application_rest_client_builder() {
    runner
        .withPropertyValues("codehunters.file-share.base-url=" + server.url())
        .withBean(
            RestClient.Builder.class, () -> RestClient.builder().defaultHeader("X-Custom", "app"))
        .run(
            context -> {
              context.getBean(FileShareClient.class).getMetadata("REF-1", FILE_ID);
              assertThat(server.customHeaders()).containsExactly("app");
            });
  }

  @Test
  void should_apply_request_interceptors_from_the_application() {
    runner
        .withPropertyValues("codehunters.file-share.base-url=" + server.url())
        .withBean(
            FileShareRequestInterceptor.class,
            () ->
                (request, body, execution) -> {
                  request.getHeaders().setBearerAuth("app-token");
                  return execution.execute(request, body);
                })
        .run(
            context -> {
              context.getBean(FileShareClient.class).getMetadata("REF-1", FILE_ID);
              assertThat(server.authorizations()).containsExactly("Bearer app-token");
            });
  }

  @Test
  void should_fail_call_when_read_timeout_elapses() {
    server.delayDocument(Duration.ofSeconds(2));
    runner
        .withPropertyValues(
            "codehunters.file-share.base-url=" + server.url(),
            "codehunters.file-share.read-timeout=200ms")
        .run(
            context ->
                assertThatThrownBy(
                        () -> context.getBean(FileShareClient.class).getMetadata("REF-1", FILE_ID))
                    .isInstanceOf(FileShareUnavailableException.class));
  }

  @Test
  void should_stream_content_slower_than_read_timeout_while_bytes_keep_flowing() {
    server.pauseBetweenContentChunks(Duration.ofMillis(150));
    var target = new ByteArrayOutputStream();
    runner
        .withPropertyValues(
            "codehunters.file-share.base-url=" + server.url(),
            "codehunters.file-share.read-timeout=200ms",
            "codehunters.file-share.download-idle-timeout=1s")
        .run(
            context ->
                assertThat(
                        context.getBean(FileShareClient.class).download("REF-1", FILE_ID, target))
                    .isEqualTo(CONTENT_SIZE));
  }

  @Test
  void should_fail_download_when_content_stalls_longer_than_idle_timeout() {
    server.pauseBetweenContentChunks(Duration.ofSeconds(2));
    runner
        .withPropertyValues(
            "codehunters.file-share.base-url=" + server.url(),
            "codehunters.file-share.download-idle-timeout=200ms")
        .run(
            context ->
                assertThatThrownBy(
                        () ->
                            context
                                .getBean(FileShareClient.class)
                                .download("REF-1", FILE_ID, new ByteArrayOutputStream()))
                    .isInstanceOf(FileShareUnavailableException.class));
  }

  @Test
  void should_not_follow_redirect_from_content_endpoint() {
    server.redirectContent();
    runner
        .withPropertyValues("codehunters.file-share.base-url=" + server.url())
        .run(
            context -> {
              assertThatThrownBy(
                      () -> context.getBean(FileShareClient.class).download("REF-1", FILE_ID))
                  .isInstanceOf(FileShareException.class);
              assertThat(server.redirectTargetHits()).isZero();
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"0B", "-1B", "2GB"})
  void should_fail_startup_when_max_download_size_cannot_be_buffered(String size) {
    runner
        .withPropertyValues(
            "codehunters.file-share.base-url=" + server.url(),
            "codehunters.file-share.max-download-size=" + size)
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("max-download-size"));
  }

  @Test
  void should_accept_base_url_with_upper_case_scheme() {
    runner
        .withPropertyValues("codehunters.file-share.base-url=" + server.url().toUpperCase())
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void should_warn_when_presigned_hosts_are_not_restricted(CapturedOutput output) {
    runner
        .withPropertyValues("codehunters.file-share.base-url=" + server.url())
        .run(context -> assertThat(context).hasSingleBean(FileShareClient.class));

    assertThat(output).contains("codehunters.file-share.presigned.allowed-hosts");
  }

  @Test
  void should_not_warn_when_presigned_hosts_are_restricted(CapturedOutput output) {
    runner
        .withPropertyValues(
            "codehunters.file-share.base-url=" + server.url(),
            "codehunters.file-share.presigned.allowed-hosts=files.example.com")
        .run(context -> assertThat(context).hasSingleBean(FileShareClient.class));

    assertThat(output).doesNotContain("codehunters.file-share.presigned.allowed-hosts");
  }

  @Test
  void should_configure_connect_timeout_on_http_client() {
    assertThat(FileShareAutoConfiguration.httpClient(Duration.ofMillis(1500)).connectTimeout())
        .contains(Duration.ofMillis(1500));
  }

  @Test
  void should_bind_defaults() {
    runner
        .withPropertyValues("codehunters.file-share.base-url=" + server.url())
        .run(
            context -> {
              FileShareProperties properties = context.getBean(FileShareProperties.class);
              assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
              assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
              assertThat(properties.downloadIdleTimeout()).isEqualTo(Duration.ofSeconds(30));
              assertThat(properties.maxDownloadSize().toMegabytes()).isEqualTo(50);
              assertThat(properties.presigned().allowedHosts()).isEmpty();
              assertThat(properties.auth().clientRegistrationId()).isNull();
              assertThat(properties.auth().principal()).isEqualTo("codehunters-file-share");
            });
  }

  record RestClientConsumer(RestClient client) {}
}
