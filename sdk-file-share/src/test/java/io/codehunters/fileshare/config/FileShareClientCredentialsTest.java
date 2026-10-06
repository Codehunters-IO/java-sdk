package io.codehunters.fileshare.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.fileshare.application.FileShareClient;
import io.codehunters.fileshare.domain.exception.FileShareUnauthorizedException;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

class FileShareClientCredentialsTest {

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
  void should_send_client_credentials_token_when_registration_configured() {
    withRegistration(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .run(
            context -> {
              context.getBean(FileShareClient.class).getMetadata("REF-1", FILE_ID);
              assertThat(server.authorizations())
                  .containsExactly("Bearer " + FakeFileShareServer.ACCESS_TOKEN);
            });
  }

  @Test
  void should_reuse_token_until_it_expires() {
    withRegistration(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .run(
            context -> {
              FileShareClient client = context.getBean(FileShareClient.class);
              client.getMetadata("REF-1", FILE_ID);
              client.getMetadata("REF-1", FILE_ID);
              assertThat(server.tokenRequests()).isEqualTo(1);
            });
  }

  @Test
  void should_fail_as_unauthorized_when_token_endpoint_rejects_client() {
    server.failTokenRequests();
    withRegistration(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .run(
            context ->
                assertThatThrownBy(
                        () -> context.getBean(FileShareClient.class).getMetadata("REF-1", FILE_ID))
                    .isInstanceOf(FileShareUnauthorizedException.class)
                    .hasMessageNotContaining(FakeFileShareServer.ACCESS_TOKEN));
  }

  @Test
  void should_send_no_token_when_registration_not_configured() {
    runner
        .withPropertyValues("codehunters.file-share.base-url=" + server.url())
        .run(
            context -> {
              context.getBean(FileShareClient.class).getMetadata("REF-1", FILE_ID);
              assertThat(server.authorizations()).containsExactly("null");
            });
  }

  @Test
  void should_let_application_interceptor_replace_client_credentials() {
    withRegistration(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .withBean(
            FileShareRequestInterceptor.class,
            () -> (request, body, execution) -> execution.execute(request, body))
        .run(
            context -> {
              context.getBean(FileShareClient.class).getMetadata("REF-1", FILE_ID);
              assertThat(server.tokenRequests()).isZero();
            });
  }

  @Test
  void should_fail_startup_when_registration_is_unknown() {
    withRegistration(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .withPropertyValues("codehunters.file-share.auth.client-registration-id=other")
        .run(context -> assertThat(context).hasFailed().getFailure().hasMessageContaining("other"));
  }

  @Test
  void should_fail_startup_when_registration_is_not_client_credentials() {
    withRegistration(AuthorizationGrantType.AUTHORIZATION_CODE)
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasMessageContaining("client_credentials"));
  }

  @Test
  void should_fail_startup_when_no_client_registrations_exist() {
    runner
        .withPropertyValues(
            "codehunters.file-share.base-url=" + server.url(),
            "codehunters.file-share.auth.client-registration-id=file-share")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasMessageContaining("ClientRegistrationRepository"));
  }

  @Test
  void should_fail_startup_when_oauth2_client_is_not_on_classpath() {
    runner
        .withClassLoader(new FilteredClassLoader(OAuth2AuthorizedClientManager.class))
        .withPropertyValues(
            "codehunters.file-share.base-url=" + server.url(),
            "codehunters.file-share.auth.client-registration-id=file-share")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasMessageContaining("spring-security-oauth2-client"));
  }

  private ApplicationContextRunner withRegistration(AuthorizationGrantType grantType) {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId("file-share")
            .clientId("sdk")
            .clientSecret("test-only")
            .authorizationGrantType(grantType)
            .redirectUri("http://localhost/callback")
            .authorizationUri(server.url() + "/authorize")
            .tokenUri(server.url() + "/token")
            .build();
    return runner
        .withPropertyValues(
            "codehunters.file-share.base-url=" + server.url(),
            "codehunters.file-share.auth.client-registration-id=file-share")
        .withBean(
            ClientRegistrationRepository.class,
            () -> new InMemoryClientRegistrationRepository(registration));
  }
}
