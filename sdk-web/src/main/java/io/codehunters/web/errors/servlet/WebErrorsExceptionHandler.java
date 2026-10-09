package io.codehunters.web.errors.servlet;

import io.codehunters.web.errors.ApiException;
import io.codehunters.web.errors.CommonErrorCode;
import io.codehunters.web.errors.ErrorCode;
import io.codehunters.web.errors.FieldViolation;
import io.codehunters.web.errors.trace.TraceIdProvider;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.DisconnectedClientHelper;
import org.springframework.web.util.WebUtils;

/**
 * Answers every exception that reaches Spring MVC with an RFC 9457 problem detail carrying a stable
 * {@code code}, a localized {@code detail}, the {@code traceId} and, for validation failures, the
 * {@code errors}. Rejected values, exception messages and stack traces never reach the client; the
 * one exception is the message of sdk-commons' {@code ClientInputException}, safe by its contract
 * and used as the detail when the bundles have no {@code error.<code>} message.
 *
 * <p>Ordered last, so an application's own {@code @RestControllerAdvice} handles the exceptions it
 * declares first. Spring Security's access and authentication exceptions are rethrown for its
 * filters to answer.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class WebErrorsExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(WebErrorsExceptionHandler.class);

  private static final String FRAMEWORK_MESSAGE_PREFIX = "web-errors.";

  private static final int MAX_CAUSE_DEPTH = 16;

  private static final Set<String> SECURITY_EXCEPTIONS =
      Set.of(
          "org.springframework.security.access.AccessDeniedException",
          "org.springframework.security.core.AuthenticationException");

  private static final boolean BEAN_VALIDATION_PRESENT =
      ClassUtils.isPresent(
          "jakarta.validation.ConstraintViolationException",
          WebErrorsExceptionHandler.class.getClassLoader());

  private static final boolean CLIENT_INPUT_PRESENT =
      ClassUtils.isPresent(
          "io.codehunters.commons.error.ClientInputException",
          WebErrorsExceptionHandler.class.getClassLoader());

  private static final Comparator<FieldViolation> BY_FIELD_AND_CODE =
      Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::code);

  private final ProblemFactory problems;

  /**
   * Creates the handler.
   *
   * @param messages resolves the localized details and violation messages
   * @param traceIds supplies the {@code traceId} extension
   * @param settings how the problem details are built
   */
  public WebErrorsExceptionHandler(
      MessageSource messages, TraceIdProvider traceIds, ProblemSettings settings) {
    this.problems = new ProblemFactory(messages, traceIds, settings);
  }

  /**
   * Answers an {@link ApiException} with the status, code and detail of its error code.
   *
   * @param exception the exception
   * @param request the current request
   * @return the problem detail response
   */
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Object> handleApiException(ApiException exception, WebRequest request) {
    ErrorCode code = exception.errorCode();
    HttpStatusCode status = StatusCodes.errorStatus(code.status());
    var spec =
        new ProblemSpec(
            status,
            code.code(),
            code.messageKey(),
            exception.arguments(),
            StatusCodes.reasonPhrase(status),
            List.of());
    return respond(exception, spec, new HttpHeaders(), request);
  }

  /**
   * Answers any exception no other handler maps: sdk-commons' {@code ClientInputException}, also as
   * a cause, with 400 and its code; constraint violations with 400; exceptions annotated with
   * {@link ResponseStatus} with their status; everything else with a bare 500.
   *
   * @param exception the exception
   * @param request the current request
   * @return the problem detail response
   * @throws Exception the exception itself when it belongs to Spring Security
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleUnmapped(Exception exception, WebRequest request)
      throws Exception {
    if (isSecurityException(exception)) {
      throw exception;
    }
    if (DisconnectedClientHelper.isClientDisconnectedException(exception)) {
      LOG.debug("Client disconnected: traceId={}", problems.loggedTraceId());
      return null;
    }
    Optional<ProblemSpec> clientInput = clientInput(exception);
    if (clientInput.isPresent()) {
      return respond(exception, clientInput.get(), new HttpHeaders(), request);
    }
    if (BEAN_VALIDATION_PRESENT && ConstraintViolations.matches(exception)) {
      return constraintViolations(exception, request);
    }
    ResponseStatus annotated =
        AnnotatedElementUtils.findMergedAnnotation(exception.getClass(), ResponseStatus.class);
    return annotated != null
        ? annotatedStatus(exception, annotated, request)
        : internalError(exception, request);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    var result = exception.getBindingResult();
    var fields =
        result.getFieldErrors().stream()
            .map(e -> problems.violation(e.getField(), e, safeMessage(e)));
    var objects =
        result.getGlobalErrors().stream()
            .map(e -> problems.violation(e.getObjectName(), e, safeMessage(e)));
    return validationFailed(exception, Stream.concat(fields, objects).toList(), status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (status.is5xxServerError()) {
      return internalError(exception, request);
    }
    var parameters =
        exception.getParameterValidationResults().stream().flatMap(this::parameterViolations);
    var crossParameter =
        exception.getCrossParameterValidationResults().stream()
            .map(error -> problems.violation("", error, Optional.empty()));
    var violations = Stream.concat(parameters, crossParameter).toList();
    return validationFailed(exception, violations, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    String code = StatusCodes.code(statusCode);
    var spec =
        new ProblemSpec(
            statusCode,
            code,
            FRAMEWORK_MESSAGE_PREFIX + code,
            List.of(),
            frameworkDetail(exception, statusCode),
            List.of());
    return respond(exception, spec, headers, request);
  }

  private Stream<FieldViolation> parameterViolations(ParameterValidationResult result) {
    String parameter = parameterName(result.getMethodParameter());
    return result.getResolvableErrors().stream()
        .map(
            error ->
                problems.violation(fieldOf(error, parameter), error, safeMessage(result, error)));
  }

  /** A rejected client input anywhere in the cause chain, when sdk-commons is present. */
  private static Optional<ProblemSpec> clientInput(Exception exception) {
    return CLIENT_INPUT_PRESENT
        ? ClientInputs.problem(exception, MAX_CAUSE_DEPTH)
        : Optional.empty();
  }

  private ResponseEntity<Object> constraintViolations(Exception exception, WebRequest request) {
    if (!ConstraintViolations.concernsArguments(exception)) {
      return internalError(exception, request);
    }
    var status = HttpStatusCode.valueOf(CommonErrorCode.VALIDATION_FAILED.status());
    return validationFailed(
        exception, ConstraintViolations.of(exception, problems), status, request);
  }

  private static Optional<String> safeMessage(ObjectError error) {
    return BEAN_VALIDATION_PRESENT ? ConstraintViolations.safeMessage(error) : Optional.empty();
  }

  private static Optional<String> safeMessage(
      ParameterValidationResult result, MessageSourceResolvable error) {
    return BEAN_VALIDATION_PRESENT
        ? ConstraintViolations.safeMessage(result, error)
        : Optional.empty();
  }

  private ResponseEntity<Object> validationFailed(
      Exception exception,
      List<FieldViolation> violations,
      HttpStatusCode status,
      WebRequest request) {
    CommonErrorCode code = CommonErrorCode.VALIDATION_FAILED;
    var sorted = violations.stream().sorted(BY_FIELD_AND_CODE).toList();
    var spec =
        new ProblemSpec(
            status,
            code.code(),
            code.messageKey(),
            List.of(),
            StatusCodes.reasonPhrase(status),
            sorted);
    return respond(exception, spec, new HttpHeaders(), request);
  }

  private ResponseEntity<Object> annotatedStatus(
      Exception exception, ResponseStatus annotated, WebRequest request) {
    HttpStatusCode status = annotated.code();
    String code = StatusCodes.code(status);
    String detail =
        StringUtils.hasText(annotated.reason())
            ? annotated.reason()
            : StatusCodes.reasonPhrase(status);
    var spec =
        new ProblemSpec(
            status, code, FRAMEWORK_MESSAGE_PREFIX + code, List.of(), detail, List.of());
    return respond(exception, spec, new HttpHeaders(), request);
  }

  private ResponseEntity<Object> internalError(Exception exception, WebRequest request) {
    CommonErrorCode code = CommonErrorCode.INTERNAL_ERROR;
    HttpStatusCode status = HttpStatusCode.valueOf(code.status());
    var spec =
        new ProblemSpec(
            status,
            code.code(),
            code.messageKey(),
            List.of(),
            StatusCodes.reasonPhrase(status),
            List.of());
    return respond(exception, spec, new HttpHeaders(), request);
  }

  /**
   * Logs and answers. A committed response cannot carry a problem detail: the failure is logged
   * once, without the exception message, and left alone. Server errors are exposed to the
   * observation filter, which otherwise sees a handled request.
   */
  private ResponseEntity<Object> respond(
      Exception exception, ProblemSpec spec, HttpHeaders headers, WebRequest request) {
    if (isCommitted(request)) {
      LOG.warn(
          "Could not answer, response already committed: status={} code={} exception={} traceId={}",
          spec.status().value(),
          spec.code(),
          exception.getClass().getName(),
          problems.loggedTraceId());
      return null;
    }
    log(spec.status(), spec.code(), exception);
    if (spec.status().is5xxServerError()) {
      request.setAttribute(
          WebUtils.ERROR_EXCEPTION_ATTRIBUTE, exception, RequestAttributes.SCOPE_REQUEST);
    }
    var problem = problems.create(spec, request);
    return super.handleExceptionInternal(exception, problem, headers, spec.status(), request);
  }

  private void log(HttpStatusCode status, String code, Exception exception) {
    String traceId = problems.loggedTraceId();
    int value = status.value();
    if (exception instanceof AsyncRequestTimeoutException) {
      LOG.warn("Request timed out: status={} code={} traceId={}", value, code, traceId);
    } else if (status.is5xxServerError()) {
      LOG.error("Request failed: status={} code={} traceId={}", value, code, traceId, exception);
    } else if (exception.getCause() != null) {
      LOG.debug("Request rejected: status={} code={} traceId={}", value, code, traceId, exception);
    } else {
      LOG.debug("Request rejected: status={} code={} traceId={}", value, code, traceId);
    }
  }

  private static boolean isCommitted(WebRequest request) {
    return request instanceof ServletWebRequest servlet
        && servlet.getResponse() != null
        && servlet.getResponse().isCommitted();
  }

  private static String frameworkDetail(Exception exception, HttpStatusCode status) {
    if (exception instanceof ResponseStatusException thrown
        && isApplicationThrown(thrown)
        && StringUtils.hasText(thrown.getReason())) {
      return thrown.getReason();
    }
    return StatusCodes.reasonPhrase(status);
  }

  /** Spring's own subclasses describe the request, so their reasons may echo client input. */
  private static boolean isApplicationThrown(ResponseStatusException exception) {
    Class<?> type = exception.getClass();
    return type == ResponseStatusException.class
        || !type.getName().startsWith("org.springframework.");
  }

  private static String fieldOf(MessageSourceResolvable error, String parameter) {
    return error instanceof FieldError fieldError ? fieldError.getField() : parameter;
  }

  private static String parameterName(MethodParameter parameter) {
    String name = parameter.getParameterName();
    return name != null ? name : "arg" + parameter.getParameterIndex();
  }

  /** Walks the cause chain too: proxies and futures wrap Spring Security's exceptions. */
  private static boolean isSecurityException(Throwable exception) {
    Throwable current = exception;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (isSecurityType(current.getClass())) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static boolean isSecurityType(Class<?> exceptionType) {
    for (Class<?> type = exceptionType; type != null; type = type.getSuperclass()) {
      if (SECURITY_EXCEPTIONS.contains(type.getName())) {
        return true;
      }
    }
    return false;
  }
}
