package io.codehunters.fileshare.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.fileshare.application.FileShareClient;
import io.codehunters.fileshare.domain.exception.FileShareUnavailableException;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class FileShareAutoConfigurationTest {

  private static final UUID FILE_ID = UUID.fromString(FakeFileShareServer.FILE_ID);

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
              assertThat(properties.maxDownloadSize().toMegabytes()).isEqualTo(50);
              assertThat(properties.presigned().allowedHosts()).isEmpty();
              assertThat(properties.auth().clientRegistrationId()).isNull();
              assertThat(properties.auth().principal()).isEqualTo("codehunters-file-share");
            });
  }

  record RestClientConsumer(RestClient client) {}
}
