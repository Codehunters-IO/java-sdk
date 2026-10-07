package io.codehunters.weberrors.config;

import io.codehunters.tracing.TraceContextPort;
import io.codehunters.weberrors.web.MdcTraceIdProvider;
import io.codehunters.weberrors.web.ProblemSettings;
import io.codehunters.weberrors.web.TraceIdProvider;
import io.codehunters.weberrors.web.WebErrorsExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Registers {@link WebErrorsExceptionHandler} in servlet applications. Runs before Boot's Web MVC
 * auto-configuration, so Boot's own problem-details handler ({@code
 * spring.mvc.problemdetails.enabled}) backs off. The SDK backs off in turn when the application
 * declares its own {@link ResponseEntityExceptionHandler} bean, or with {@code
 * codehunters.web-errors.enabled=false}.
 */
@AutoConfiguration(
    beforeName = "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration",
    afterName = "io.codehunters.tracing.config.TracingAutoConfiguration")
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({ResponseEntityExceptionHandler.class, ProblemDetail.class})
@ConditionalOnBooleanProperty(name = "codehunters.web-errors.enabled", matchIfMissing = true)
@EnableConfigurationProperties(WebErrorsProperties.class)
public class WebErrorsAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public WebErrorsAutoConfiguration() {}

  /**
   * The exception handler. It resolves messages through the application context, which delegates to
   * the application's {@code messageSource} (the i18n SDK's, when present).
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
    var settings = new ProblemSettings(properties.typeBaseUri(), properties.includeTraceId());
    return new WebErrorsExceptionHandler(context, traceIds, settings);
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

  /** Uses the tracing SDK's port when the application has it. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(TraceContextPort.class)
  static class TracingConfiguration {

    @Bean
    @ConditionalOnMissingBean(TraceIdProvider.class)
    @ConditionalOnBean(TraceContextPort.class)
    TraceIdProvider tracingTraceIdProvider(TraceContextPort traceContext) {
      return traceContext::currentTraceId;
    }
  }
}
