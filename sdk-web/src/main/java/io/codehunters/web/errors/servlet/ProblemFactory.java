package io.codehunters.web.errors.servlet;

import io.codehunters.web.errors.FieldViolation;
import io.codehunters.web.errors.trace.TraceIdProvider;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/**
 * Builds localized problem details with the SDK's extensions.
 *
 * <p>Messages are resolved from the application's bundles only. A default message is never rendered
 * through {@code MessageFormat}: Spring's defaults for binding failures, and Bean Validation
 * messages that reference the validated value, carry client input, which would leak into the
 * response and grow {@code MessageFormat} caches without bound.
 */
final class ProblemFactory {

  private static final String INVALID_VALUE = "Invalid value";
  private static final String TYPE_MISMATCH_KEY = "web-errors.typeMismatch";
  private static final String INVALID_KEY = "web-errors.invalid";
  private static final Pattern PATH_PARAMETERS = Pattern.compile(";[^/]*");

  private final MessageSource messages;
  private final TraceIdProvider traceIds;
  private final ProblemSettings settings;

  ProblemFactory(MessageSource messages, TraceIdProvider traceIds, ProblemSettings settings) {
    this.messages = messages;
    this.traceIds = traceIds;
    this.settings = settings;
  }

  ProblemDetail create(ProblemSpec spec, WebRequest request) {
    var problem = ProblemDetail.forStatusAndDetail(spec.status(), detail(spec));
    problem.setTitle(StatusCodes.reasonPhrase(spec.status()));
    typeOf(spec.code()).ifPresent(problem::setType);
    instanceOf(request).ifPresent(problem::setInstance);
    problem.setProperty("code", spec.code());
    traceId().ifPresent(id -> problem.setProperty("traceId", id));
    if (!spec.violations().isEmpty()) {
      problem.setProperty("errors", spec.violations());
    }
    return problem;
  }

  /**
   * A violation whose message comes from the bundles, else from a default known to be free of
   * client input, else from a fixed fallback.
   */
  FieldViolation violation(
      String field, MessageSourceResolvable error, Optional<String> safeDefault) {
    String code = mostGeneralCode(error);
    String message = bundleMessage(error).or(() -> safeDefault).orElseGet(() -> fallback(error));
    return new FieldViolation(field, code, message);
  }

  Optional<String> traceId() {
    return settings.includeTraceId() ? currentTraceId() : Optional.empty();
  }

  /** The trace id for log lines, whether or not responses include it. */
  String loggedTraceId() {
    return currentTraceId().orElse("none");
  }

  private Optional<String> currentTraceId() {
    return traceIds.currentTraceId().filter(id -> !id.isBlank());
  }

  private String detail(ProblemSpec spec) {
    Object[] arguments = spec.arguments().toArray();
    Locale locale = LocaleContextHolder.getLocale();
    try {
      return messages.getMessage(spec.messageKey(), arguments, locale);
    } catch (NoSuchMessageException missing) {
      return spec.defaultDetail();
    }
  }

  private Optional<String> bundleMessage(MessageSourceResolvable error) {
    var codesOnly =
        new DefaultMessageSourceResolvable(error.getCodes(), error.getArguments(), null);
    try {
      return Optional.of(messages.getMessage(codesOnly, LocaleContextHolder.getLocale()));
    } catch (NoSuchMessageException missing) {
      return Optional.empty();
    }
  }

  private String fallback(MessageSourceResolvable error) {
    boolean bindingFailure = error instanceof FieldError field && field.isBindingFailure();
    String key = bindingFailure ? TYPE_MISMATCH_KEY : INVALID_KEY;
    return messages.getMessage(key, null, INVALID_VALUE, LocaleContextHolder.getLocale());
  }

  private Optional<URI> typeOf(String code) {
    String slug = code.toLowerCase(Locale.ROOT).replace('_', '-');
    return Optional.ofNullable(settings.typeBaseUri())
        .map(base -> base.resolve(URLEncoder.encode(slug, StandardCharsets.UTF_8)));
  }

  /**
   * The path the client requested: the original one on an error dispatch, without path parameters
   * such as {@code ;jsessionid=...}.
   */
  private static Optional<URI> instanceOf(WebRequest request) {
    if (!(request instanceof ServletWebRequest servletRequest)) {
      return Optional.empty();
    }
    HttpServletRequest servlet = servletRequest.getRequest();
    Object original = servlet.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
    String path = original instanceof String uri ? uri : servlet.getRequestURI();
    try {
      return Optional.of(new URI(PATH_PARAMETERS.matcher(path).replaceAll("")));
    } catch (URISyntaxException invalid) {
      return Optional.empty();
    }
  }

  private static String mostGeneralCode(MessageSourceResolvable error) {
    String[] codes = error.getCodes();
    return codes == null || codes.length == 0 ? "" : codes[codes.length - 1];
  }
}
