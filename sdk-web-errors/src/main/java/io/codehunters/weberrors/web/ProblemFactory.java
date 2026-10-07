package io.codehunters.weberrors.web;

import io.codehunters.weberrors.FieldViolation;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ProblemDetail;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/** Builds localized problem details with the SDK's extensions. */
final class ProblemFactory {

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

  FieldViolation violation(String field, MessageSourceResolvable error) {
    String code = mostGeneralCode(error);
    return new FieldViolation(field, code, message(error, code));
  }

  Optional<String> traceId() {
    return settings.includeTraceId() ? traceIds.currentTraceId() : Optional.empty();
  }

  private String detail(ProblemSpec spec) {
    Object[] arguments = spec.arguments().toArray();
    Locale locale = LocaleContextHolder.getLocale();
    return messages.getMessage(spec.messageKey(), arguments, spec.defaultDetail(), locale);
  }

  private String message(MessageSourceResolvable error, String fallback) {
    try {
      return messages.getMessage(error, LocaleContextHolder.getLocale());
    } catch (NoSuchMessageException missing) {
      return fallback;
    }
  }

  private Optional<URI> typeOf(String code) {
    String slug = code.toLowerCase(Locale.ROOT).replace('_', '-');
    return Optional.ofNullable(settings.typeBaseUri())
        .map(base -> base.resolve(URLEncoder.encode(slug, StandardCharsets.UTF_8)));
  }

  private static Optional<URI> instanceOf(WebRequest request) {
    if (!(request instanceof ServletWebRequest servletRequest)) {
      return Optional.empty();
    }
    try {
      return Optional.of(new URI(servletRequest.getRequest().getRequestURI()));
    } catch (URISyntaxException invalid) {
      return Optional.empty();
    }
  }

  private static String mostGeneralCode(MessageSourceResolvable error) {
    String[] codes = error.getCodes();
    return codes == null || codes.length == 0 ? "" : codes[codes.length - 1];
  }
}
