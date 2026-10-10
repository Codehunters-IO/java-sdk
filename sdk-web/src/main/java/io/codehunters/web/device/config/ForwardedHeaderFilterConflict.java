package io.codehunters.web.device.config;

import java.util.stream.Stream;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Refuses the SDK's client IP filter next to Spring's {@code ForwardedHeaderFilter}. Both claim the
 * first place in the chain, so their order is undefined, and when Spring's runs first it rewrites
 * the remote address from the leftmost, client-written forwarded value before the SDK can check it
 * against the trusted proxies.
 */
final class ForwardedHeaderFilterConflict {

  private static final String FILTER_PROPERTY =
      "codehunters.web.device.forwarded-request-filter.enabled=true";
  private static final String FIX =
      ": it takes the leftmost Forwarded or X-Forwarded-For value from any client. Keep one: remove"
          + " the framework strategy (or the bean), or use server.forward-headers-strategy=native"
          + " and disable the SDK filter.";

  private ForwardedHeaderFilterConflict() {}

  static void check(Environment environment, ListableBeanFactory beans) {
    if ("framework".equalsIgnoreCase(environment.getProperty("server.forward-headers-strategy"))) {
      throw conflict("server.forward-headers-strategy=framework (Spring's ForwardedHeaderFilter)");
    }
    forwardedHeaderFilterBeans(beans)
        .findFirst()
        .ifPresent(
            name -> {
              throw conflict("Spring's ForwardedHeaderFilter (bean '" + name + "')");
            });
  }

  private static Stream<String> forwardedHeaderFilterBeans(ListableBeanFactory beans) {
    var registration =
        ResolvableType.forClassWithGenerics(
            FilterRegistrationBean.class, ForwardedHeaderFilter.class);
    return Stream.concat(
        Stream.of(beans.getBeanNamesForType(ForwardedHeaderFilter.class, true, false)),
        Stream.of(beans.getBeanNamesForType(registration, true, false)));
  }

  private static IllegalStateException conflict(String other) {
    return new IllegalStateException(FILTER_PROPERTY + " cannot be combined with " + other + FIX);
  }
}
