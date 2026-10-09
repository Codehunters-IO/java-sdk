package io.codehunters.web.errors.config;

import io.codehunters.tracing.TraceContextPort;
import io.codehunters.web.errors.security.ProblemAccessDeniedHandler;
import io.codehunters.web.errors.security.ProblemAuthenticationEntryPoint;
import io.codehunters.web.errors.servlet.ProblemErrorController;
import io.codehunters.web.errors.servlet.ProblemSettings;
import io.codehunters.web.errors.servlet.ProblemWriter;
import io.codehunters.web.errors.servlet.WebErrorsExceptionHandler;
import io.codehunters.web.errors.trace.MdcTraceIdProvider;
import io.codehunters.web.errors.trace.TraceIdProvider;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.ProblemDetail;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Registers {@link WebErrorsExceptionHandler} in servlet applications. Runs before Boot's Web MVC
 * auto-configuration, so Boot's own problem-details handler ({@code
 * spring.mvc.problemdetails.enabled}) backs off. The SDK backs off in turn when the application
 * declares its own {@link ResponseEntityExceptionHandler} bean, or with {@code
 * codehunters.web.errors.enabled=false}.
 *
 * <p>Also replaces Boot's error controller with {@link ProblemErrorController}, and, with Spring
 * Security, offers a {@link ProblemAuthenticationEntryPoint} and a {@link
 * ProblemAccessDeniedHandler} for the application's filter chain.
 */
@AutoConfiguration(
    beforeName = {
      "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration",
      "org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration"
    },
    afterName = {
      "io.codehunters.tracing.config.TracingAutoConfiguration",
      "org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration"
    })
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({ResponseEntityExceptionHandler.class, ProblemDetail.class})
@ConditionalOnBooleanProperty(name = "codehunters.web.errors.enabled", matchIfMissing = true)
@EnableConfigurationProperties(WebErrorsProperties.class)
public class WebErrorsAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public WebErrorsAutoConfiguration() {}

  /**
   * Adds the SDK's error bundle to the application's {@code messageSource}, after its own bundles.
   *
   * @return the post-processor
   */
  @Bean
  static BeanPostProcessor errorBundleRegistrar() {
    return new ErrorBundleRegistrar();
  }

  /**
   * A {@code messageSource} holding only the SDK's error bundle, for applications without bundles
   * of their own: Boot then registers none, and the context's empty placeholder is never
   * post-processed. Backs off when any {@code messageSource} bean exists, Boot's included.
   *
   * @return the message source
   */
  @Bean(name = AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME)
  @ConditionalOnMissingBean(
      name = AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME,
      search = SearchStrategy.CURRENT)
  public ResourceBundleMessageSource messageSource() {
    var messages = new ResourceBundleMessageSource();
    messages.setBasename(ErrorBundleRegistrar.BASENAME);
    messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
    messages.setFallbackToSystemLocale(false);
    return messages;
  }

  /**
   * Warns at startup when the SDK's error bundle cannot be resolved through the context.
   *
   * @param context the application context, used as message source
   * @return the check
   */
  @Bean
  static SmartInitializingSingleton errorBundleCheck(ApplicationContext context) {
    return new ErrorBundleCheck(context);
  }

  /**
   * The exception handler. It resolves messages through the application context, which delegates to
   * the application's {@code messageSource} (Boot's, configured by {@code spring.messages.*}).
   *
   * @param context the application context, used as message source
   * @param traceIds the trace id provider
   * @param properties the SDK configuration
   * @return the handler
   */
  @Bean
  @ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)
  public WebErrorsExceptionHandler webErrorsExceptionHandler(
      ApplicationContext context, TraceIdProvider traceIds, WebErrorsProperties properties) {
    return new WebErrorsExceptionHandler(context, traceIds, settings(properties));
  }

  /**
   * Reads the trace id from the MDC when the tracing SDK is absent.
   *
   * @return the MDC-backed provider
   */
  @Bean
  @ConditionalOnMissingBean
  public TraceIdProvider traceIdProvider() {
    return new MdcTraceIdProvider();
  }

  private static ProblemSettings settings(WebErrorsProperties properties) {
    return new ProblemSettings(properties.typeBaseUri(), properties.includeTraceId());
  }

  /** Answers the container's error dispatch, unless the application has its own controller. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(ErrorController.class)
  static class ErrorControllerConfiguration {

    @Bean
    @ConditionalOnMissingBean(value = ErrorController.class, search = SearchStrategy.CURRENT)
    ProblemErrorController problemErrorController(
        ApplicationContext context, TraceIdProvider traceIds, WebErrorsProperties properties) {
      return new ProblemErrorController(context, traceIds, settings(properties));
    }
  }

  /**
   * Problem-detail answers for Spring Security's 401 and 403. The application wires them into its
   * filter chain; Spring Security never picks entry points up from the context by itself.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(
      name = {
        "org.springframework.security.web.AuthenticationEntryPoint",
        "tools.jackson.databind.json.JsonMapper"
      })
  static class SecurityConfiguration {

    /** Resolves the locale with the application's {@code localeResolver} when there is one. */
    @Bean
    @ConditionalOnMissingBean
    ProblemWriter problemWriter(
        ApplicationContext context,
        TraceIdProvider traceIds,
        WebErrorsProperties properties,
        @Qualifier(DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
            ObjectProvider<LocaleResolver> localeResolver) {
      ProblemWriter writer = new ProblemWriter(context, traceIds, settings(properties));
      return Optional.ofNullable(localeResolver.getIfAvailable())
          .map(writer::withLocaleResolver)
          .orElse(writer);
    }

    @Bean
    @ConditionalOnMissingBean
    ProblemAuthenticationEntryPoint problemAuthenticationEntryPoint(ProblemWriter writer) {
      return new ProblemAuthenticationEntryPoint(
          (request, response, exception) -> response.setStatus(401), writer);
    }

    @Bean
    @ConditionalOnMissingBean
    ProblemAccessDeniedHandler problemAccessDeniedHandler(ProblemWriter writer) {
      return new ProblemAccessDeniedHandler(
          (request, response, exception) -> response.setStatus(403), writer);
    }

    /** Keeps the OAuth2 bearer challenge headers when the resource server is present. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(BearerTokenAuthenticationEntryPoint.class)
    static class BearerChallengeConfiguration {

      @Bean
      @ConditionalOnMissingBean
      ProblemAuthenticationEntryPoint bearerProblemAuthenticationEntryPoint(ProblemWriter writer) {
        return new ProblemAuthenticationEntryPoint(
            new BearerTokenAuthenticationEntryPoint(), writer);
      }

      @Bean
      @ConditionalOnMissingBean
      ProblemAccessDeniedHandler bearerProblemAccessDeniedHandler(ProblemWriter writer) {
        return new ProblemAccessDeniedHandler(new BearerTokenAccessDeniedHandler(), writer);
      }
    }
  }

  /** Uses the tracing SDK's port when the application has it. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(TraceContextPort.class)
  static class TracingConfiguration {

    @Bean
    @ConditionalOnMissingBean(TraceIdProvider.class)
    @ConditionalOnBean(TraceContextPort.class)
    TraceIdProvider tracingTraceIdProvider(TraceContextPort traceContext) {
      return () -> traceContext.currentTraceId().filter(id -> !id.isBlank());
    }
  }
}
