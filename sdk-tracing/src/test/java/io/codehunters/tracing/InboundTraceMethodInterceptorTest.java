package io.codehunters.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class InboundTraceMethodInterceptorTest {

  private InboundTraceMethodInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new InboundTraceMethodInterceptor();
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  private Message messageWithHeaders(String trace, String span) {
    MessageProperties props = new MessageProperties();
    if (trace != null) {
      props.setHeader("x-trace-id", trace);
    }
    if (span != null) {
      props.setHeader("x-span-id", span);
    }
    return new Message(new byte[0], props);
  }

  @Test
  @DisplayName("should_set_mdc_from_message_headers_when_invoked")
  void should_set_mdc_from_headers() throws Throwable {
    Message msg = messageWithHeaders("tr-1", "sp-1");
    MethodInvocation inv = mock(MethodInvocation.class);
    when(inv.getArguments()).thenReturn(new Object[] {msg});
    String[] insideTrace = new String[1];
    when(inv.proceed())
        .thenAnswer(
            i -> {
              insideTrace[0] = MDC.get("traceId");
              return "ok";
            });

    Object result = interceptor.invoke(inv);

    assertThat(result).isEqualTo("ok");
    assertThat(insideTrace[0]).isEqualTo("tr-1");
    assertThat(MDC.get("traceId")).isNull();
    assertThat(MDC.get("spanId")).isNull();
  }

  @Test
  @DisplayName("should_not_overwrite_existing_mdc_when_already_set")
  void should_preserve_existing_mdc() throws Throwable {
    MDC.put("traceId", "pre-existing");
    Message msg = messageWithHeaders("incoming", null);
    MethodInvocation inv = mock(MethodInvocation.class);
    when(inv.getArguments()).thenReturn(new Object[] {msg});
    when(inv.proceed()).thenReturn(null);

    interceptor.invoke(inv);

    assertThat(MDC.get("traceId")).isEqualTo("pre-existing");
  }

  @Test
  @DisplayName("should_skip_mdc_when_no_message_in_args")
  void should_skip_when_no_message() throws Throwable {
    MethodInvocation inv = mock(MethodInvocation.class);
    when(inv.getArguments()).thenReturn(new Object[] {"not-a-message", 42});
    when(inv.proceed()).thenReturn("done");

    Object result = interceptor.invoke(inv);

    assertThat(result).isEqualTo("done");
    assertThat(MDC.get("traceId")).isNull();
  }

  @Test
  @DisplayName("should_handle_null_args_when_invocation_returns_null")
  void should_handle_null_args() throws Throwable {
    MethodInvocation inv = mock(MethodInvocation.class);
    when(inv.getArguments()).thenReturn(null);
    when(inv.proceed()).thenReturn(null);

    interceptor.invoke(inv);

    assertThat(MDC.get("traceId")).isNull();
  }

  @Test
  @DisplayName("should_skip_missing_header_when_only_one_provided")
  void should_skip_missing_header() throws Throwable {
    Message msg = messageWithHeaders("tr-only", null);
    MethodInvocation inv = mock(MethodInvocation.class);
    when(inv.getArguments()).thenReturn(new Object[] {msg});
    String[] insideSpan = new String[1];
    when(inv.proceed())
        .thenAnswer(
            i -> {
              insideSpan[0] = MDC.get("spanId");
              return null;
            });

    interceptor.invoke(inv);

    assertThat(insideSpan[0]).isNull();
  }
}
