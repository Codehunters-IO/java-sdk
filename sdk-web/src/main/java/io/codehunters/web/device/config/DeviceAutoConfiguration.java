package io.codehunters.web.device.config;

import io.codehunters.web.device.cache.CachingUserAgentParser;
import io.codehunters.web.device.ip.ClientIpResolver;
import io.codehunters.web.device.ip.TrustedProxies;
import io.codehunters.web.device.servlet.ClientContextResolver;
import io.codehunters.web.device.servlet.ForwardedClientIpFilter;
import io.codehunters.web.device.uap.UapUserAgentParser;
import io.codehunters.web.device.useragent.UnknownUserAgentParser;
import io.codehunters.web.device.useragent.UserAgentParser;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers, in servlet applications, the {@link ClientIpResolver}, a {@link UserAgentParser}
 * (uap-java when present, behind a Caffeine cache), the {@link ClientContextResolver} and, when
 * enabled, the {@link ForwardedClientIpFilter}. Other applications get nothing: the plain-Java core
 * is constructed by hand there. Every bean backs off when the application defines its own; {@code
 * codehunters.web.device.enabled=false} disables the SDK.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(
    name = {
      "jakarta.servlet.http.HttpServletRequest",
      "org.springframework.web.context.request.RequestContextHolder"
    })
@ConditionalOnBooleanProperty(name = "codehunters.web.device.enabled", matchIfMissing = true)
@EnableConfigurationProperties(DeviceProperties.class)
public class DeviceAutoConfiguration {

  /** Name of the User-Agent cache in the {@code cache.*} metrics. */
  public static final String USER_AGENT_CACHE_NAME = "codehunters.web.device.user-agent.cache";

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public DeviceAutoConfiguration() {}

  /**
   * Resolves client addresses behind the configured trusted proxies.
   *
   * @param properties the SDK configuration
   * @return the resolver
   */
  @Bean
  @ConditionalOnMissingBean
  public ClientIpResolver clientIpResolver(DeviceProperties properties) {
    return new ClientIpResolver(
        TrustedProxies.of(properties.trustedProxies()), properties.forwardingHeader());
  }

  /**
   * Describes every client as unknown when uap-java is absent.
   *
   * @return the fallback parser
   */
  @Bean
  @ConditionalOnMissingBean
  public UserAgentParser userAgentParser() {
    return new UnknownUserAgentParser();
  }

  /**
   * Resolves the client context of servlet requests.
   *
   * @param ipResolver the client address resolver
   * @param parser the User-Agent parser
   * @param properties the SDK configuration
   * @return the resolver
   */
  @Bean
  @ConditionalOnMissingBean
  public ClientContextResolver clientContextResolver(
      ClientIpResolver ipResolver, UserAgentParser parser, DeviceProperties properties) {
    return new ClientContextResolver(ipResolver, parser, properties.userAgent().maxLength());
  }

  /**
   * Rewrites the remote address to the resolved client, when enabled.
   *
   * @param ipResolver the client address resolver
   * @return the filter, registered by Spring Boot first in the chain
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBooleanProperty(name = "codehunters.web.device.forwarded-request-filter.enabled")
  public ForwardedClientIpFilter forwardedClientIpFilter(ClientIpResolver ipResolver) {
    return new ForwardedClientIpFilter(ipResolver);
  }

  /** Parses with uap-java, behind a bounded cache, when the library is on the classpath. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "ua_parser.Parser")
  static class UapConfiguration {

    @Bean
    @ConditionalOnMissingBean
    UserAgentParser uapUserAgentParser(DeviceProperties properties) {
      return new CachingUserAgentParser(
          new UapUserAgentParser(), properties.userAgent().cacheSize());
    }
  }

  /** Publishes the User-Agent cache statistics when Micrometer is on the classpath. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "io.micrometer.core.instrument.binder.MeterBinder")
  static class MetricsConfiguration {

    /**
     * Binds {@code cache.*} meters tagged {@code cache=codehunters.web.device.user-agent.cache} to
     * every registry Spring Boot hands it; binds nothing when the parser keeps no cache.
     */
    @Bean
    MeterBinder userAgentCacheMetrics(ObjectProvider<UserAgentParser> parser) {
      return registry -> parser.ifUnique(candidate -> bind(candidate, registry));
    }

    private static void bind(UserAgentParser parser, MeterRegistry registry) {
      if (parser instanceof CachingUserAgentParser caching) {
        caching
            .cache()
            .map(cache -> new CaffeineCacheMetrics<>(cache, USER_AGENT_CACHE_NAME, Tags.empty()))
            .ifPresent(metrics -> metrics.bindTo(registry));
      }
    }
  }
}
