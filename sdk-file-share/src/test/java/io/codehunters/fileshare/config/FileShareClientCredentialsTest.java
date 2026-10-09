package io.codehunters.fileshare.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.fileshare.FileShareClient;
import io.codehunters.fileshare.FileShareRequestInterceptor;
import io.codehunters.fileshare.FileShareUnauthorizedException;
import io.codehunters.fileshare.FileShareUnavailableException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

class FileShareClientCredentialsTest {

  private static final UUID FILE_ID = UUID.fromString(FakeFileShareServer.FILE_ID);
  private static final String APP_TOKEN = "app-token";

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
  void should_use_the_application_authorized_client_manager_when_it_declares_one() {
    withRegistration(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .withBean(
            OAuth2AuthorizedClientManager.class,
            () -> request -> authorizedClient(request.getClientRegistrationId()))
        .run(
            context -> {
              context.getBean(FileShareClient.class).getMetadata("REF-1", FILE_ID);
              assertThat(server.authorizations()).containsExactly("Bearer " + APP_TOKEN);
              assertThat(server.tokenRequests()).isZero();
            });
  }

  @Test
  void should_keep_its_own_manager_when_the_application_has_only_the_request_bound_one() {
    withRegistration(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .withBean(
            OAuth2AuthorizedClientManager.class,
            () ->
                new DefaultOAuth2AuthorizedClientManager(
                    repository(),
                    new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(
                        new InMemoryOAuth2AuthorizedClientService(repository()))))
        .run(
            context -> {
              context.getBean(FileShareClient.class).getMetadata("REF-1", FILE_ID);
              assertThat(server.authorizations())
                  .containsExactly("Bearer " + FakeFileShareServer.ACCESS_TOKEN);
            });
  }

  @Test
  void should_upload_without_the_service_token_or_application_interceptors() {
    withRegistration(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .withBean(
            FileShareRequestInterceptor.class,
            () ->
                (request, body, execution) -> {
                  request.getHeaders().add("X-Custom", "app");
                  return execution.execute(request, body);
                })
        .run(
            context -> {
              FileShareAutoConfigurationTest.put(
                  FileShareAutoConfigurationTest.uploadClient(context), server.url() + "/upload");
              assertThat(server.uploadHeaders()).containsExactly("null|null");
              assertThat(server.tokenRequests()).isZero();
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
  void should_keep_client_credentials_when_application_declares_interceptor() {
    withRegistration(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .withBean(
            FileShareRequestInterceptor.class,
            () ->
                (request, body, execution) -> {
                  request.getHeaders().add("X-Custom", "app");
                  return execution.execute(request, body);
                })
        .run(
            context -> {
              context.getBean(FileShareClient.class).getMetadata("REF-1", FILE_ID);
              assertThat(server.authorizations())
                  .containsExactly("Bearer " + FakeFileShareServer.ACCESS_TOKEN);
              assertThat(server.customHeaders()).containsExactly("app");
            });
  }

  @Test
  void should_fetch_new_token_after_service_rejects_cached_one() {
    withRegistration(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .run(
            context -> {
              FileShareClient client = context.getBean(FileShareClient.class);
              client.getMetadata("REF-1", FILE_ID);
              server.rejectNextDocumentRequest();
              assertThatThrownBy(() -> client.getMetadata("REF-1", FILE_ID))
                  .isInstanceOf(FileShareUnauthorizedException.class);
              client.getMetadata("REF-1", FILE_ID);
              assertThat(server.tokenRequests()).isEqualTo(2);
            });
  }

  @Test
  void should_fail_as_unavailable_when_token_endpoint_hangs() {
    server.delayToken(Duration.ofSeconds(2));
    withRegistration(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .withPropertyValues("codehunters.file-share.read-timeout=200ms")
        .run(
            context ->
                assertThatThrownBy(
                        () -> context.getBean(FileShareClient.class).getMetadata("REF-1", FILE_ID))
                    .isInstanceOf(FileShareUnavailableException.class));
  }

  @Test
  void should_fail_as_unavailable_when_token_endpoint_is_unreachable() {
    withRegistration(AuthorizationGrantType.CLIENT_CREDENTIALS, "http://127.0.0.1:1/token")
        .run(
            context ->
                assertThatThrownBy(
                        () -> context.getBean(FileShareClient.class).getMetadata("REF-1", FILE_ID))
                    .isInstanceOf(FileShareUnavailableException.class));
  }

  @Test
  void should_fail_startup_when_auth_targets_plain_http_base_url() {
    withRegistration(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .withPropertyValues("codehunters.file-share.auth.allow-insecure-http=false")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("https")
                    .hasMessageContaining("allow-insecure-http"));
  }

  @Test
  void should_send_no_token_when_registration_id_is_blank() {
    runner
        .withPropertyValues(
            "codehunters.file-share.base-url=" + server.url(),
            "codehunters.file-share.auth.client-registration-id=")
        .run(
            context -> {
              context.getBean(FileShareClient.class).getMetadata("REF-1", FILE_ID);
              assertThat(server.authorizations()).containsExactly("null");
            });
  }

  @Test
  void should_work_without_spring_security_on_classpath() {
    runner
        .withClassLoader(new FilteredClassLoader("org.springframework.security"))
        .withPropertyValues("codehunters.file-share.base-url=" + server.url())
        .run(
            context -> {
              context.getBean(FileShareClient.class).getMetadata("REF-1", FILE_ID);
              assertThat(server.authorizations()).containsExactly("null");
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

  private OAuth2AuthorizedClient authorizedClient(String registrationId) {
    var token =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            APP_TOKEN,
            Instant.now(),
            Instant.now().plusSeconds(300));
    return new OAuth2AuthorizedClient(registration(registrationId), "app", token);
  }

  private ClientRegistrationRepository repository() {
    return new InMemoryClientRegistrationRepository(registration("file-share"));
  }

  private ClientRegistration registration(String registrationId) {
    return ClientRegistration.withRegistrationId(registrationId)
        .clientId("app")
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .tokenUri(server.url() + "/token")
        .build();
  }

  private ApplicationContextRunner withRegistration(AuthorizationGrantType grantType) {
    return withRegistration(grantType, server.url() + "/token");
  }

  private ApplicationContextRunner withRegistration(
      AuthorizationGrantType grantType, String tokenUri) {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId("file-share")
            .clientId("sdk")
            .clientSecret("test-only")
            .authorizationGrantType(grantType)
            .redirectUri("http://localhost/callback")
            .authorizationUri(server.url() + "/authorize")
            .tokenUri(tokenUri)
            .build();
    return runner
        .withPropertyValues(
            "codehunters.file-share.base-url=" + server.url(),
            "codehunters.file-share.auth.client-registration-id=file-share",
            "codehunters.file-share.auth.allow-insecure-http=true")
        .withBean(
            ClientRegistrationRepository.class,
            () -> new InMemoryClientRegistrationRepository(registration));
  }
}
