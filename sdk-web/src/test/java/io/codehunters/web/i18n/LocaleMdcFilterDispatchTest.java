package io.codehunters.web.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import org.springframework.web.util.WebUtils;

@ExtendWith(OutputCaptureExtension.class)
class LocaleMdcFilterDispatchTest {

  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final AtomicReference<String> seen = new AtomicReference<>();
  private final FilterChain chain = (req, res) -> seen.set(MDC.get(LocaleMdcFilter.MDC_LOCALE));

  @BeforeEach
  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void should_put_locale_in_mdc_when_request_is_an_async_dispatch() throws Exception {
    request.setDispatcherType(DispatcherType.ASYNC);

    new LocaleMdcFilter(new FixedLocaleResolver(Locale.of("en")))
        .doFilter(request, response, chain);

    assertThat(seen.get()).isEqualTo("en");
  }

  @Test
  void should_put_locale_in_mdc_when_request_is_an_error_dispatch() throws Exception {
    request.setDispatcherType(DispatcherType.ERROR);
    request.setAttribute(WebUtils.ERROR_REQUEST_URI_ATTRIBUTE, "/orders");

    new LocaleMdcFilter(new FixedLocaleResolver(Locale.of("en")))
        .doFilter(request, response, chain);

    assertThat(seen.get()).isEqualTo("en");
  }

  @Test
  void should_log_failure_class_without_message_when_resolver_throws(CapturedOutput output)
      throws Exception {
    new LocaleMdcFilter(failingResolver("header=secret-value")).doFilter(request, response, chain);

    assertThat(output)
        .contains("Could not resolve the request locale")
        .contains(IllegalStateException.class.getName())
        .doesNotContain("secret-value");
  }

  @Test
  void should_continue_the_chain_when_resolver_throws() throws Exception {
    seen.set("not-called");

    new LocaleMdcFilter(failingResolver("boom")).doFilter(request, response, chain);

    assertThat(seen.get()).isNull();
  }

  private LocaleResolver failingResolver(String message) {
    LocaleResolver resolver = mock(LocaleResolver.class);
    when(resolver.resolveLocale(request)).thenThrow(new IllegalStateException(message));
    return resolver;
  }
}
