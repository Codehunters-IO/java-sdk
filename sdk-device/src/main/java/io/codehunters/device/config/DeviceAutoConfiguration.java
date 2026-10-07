package io.codehunters.device.config;

import io.codehunters.device.ip.ClientIpResolver;
import io.codehunters.device.ip.TrustedProxies;
import io.codehunters.device.servlet.ClientContextResolver;
import io.codehunters.device.uap.UapUserAgentParser;
import io.codehunters.device.useragent.CachingUserAgentParser;
import io.codehunters.device.useragent.UnknownUserAgentParser;
import io.codehunters.device.useragent.UserAgentParser;
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
 * Registers the {@link ClientIpResolver}, a {@link UserAgentParser} (uap-java when present, cached)
 * and, in servlet applications, the {@link ClientContextResolver}. Every bean backs off when the
 * application defines its own; {@code codehunters.device.enabled=false} disables the SDK.
 */
@AutoConfiguration
@ConditionalOnBooleanProperty(name = "codehunters.device.enabled", matchIfMissing = true)
@EnableConfigurationProperties(DeviceProperties.class)
public class DeviceAutoConfiguration {

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
    return new ClientIpResolver(TrustedProxies.of(properties.trustedProxies()));
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

  /** Resolves the client context of servlet requests. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = Type.SERVLET)
  @ConditionalOnClass(
      name = {
        "jakarta.servlet.http.HttpServletRequest",
        "org.springframework.web.context.request.RequestContextHolder"
      })
  static class ServletConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ClientContextResolver clientContextResolver(
        ClientIpResolver ipResolver, UserAgentParser parser, DeviceProperties properties) {
      return new ClientContextResolver(ipResolver, parser, properties.userAgent().maxLength());
    }
  }
}
