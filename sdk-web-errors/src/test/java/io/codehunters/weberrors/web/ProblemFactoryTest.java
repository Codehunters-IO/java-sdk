package io.codehunters.weberrors.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

class ProblemFactoryTest {

  private static final ProblemSpec NOT_FOUND =
      new ProblemSpec(HttpStatus.NOT_FOUND, "NOT_FOUND", "web-errors.NOT_FOUND", List.of(), "Not Found", List.of());

  private final ProblemFactory factory =
      new ProblemFactory(
          new StaticMessageSource(), Optional::empty, new ProblemSettings(null, true));

  @Test
  void should_fall_back_to_the_most_general_code_when_a_violation_has_no_message() {
    var error = new DefaultMessageSourceResolvable(new String[] {"Custom.order.name", "Custom"});

    assertThat(factory.violation("name", error).message()).isEqualTo("Custom");
  }

  @Test
  void should_use_an_empty_code_when_a_violation_has_no_codes() {
    var error = new DefaultMessageSourceResolvable(null, null, "must be valid");

    assertThat(factory.violation("name", error).code()).isEmpty();
  }

  @Test
  void should_omit_the_instance_when_the_request_path_is_not_a_valid_uri() {
    var request = new MockHttpServletRequest("GET", "/orders/a b");

    assertThat(factory.create(NOT_FOUND, new ServletWebRequest(request)).getInstance()).isNull();
  }

  @Test
  void should_omit_the_instance_when_the_request_is_not_a_servlet_request() {
    WebRequest request = mock(WebRequest.class);

    assertThat(factory.create(NOT_FOUND, request).getInstance()).isNull();
  }
}
