package io.codehunters.web.errors.servlet;

import io.codehunters.commons.error.ClientInputException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Reads sdk-commons' {@link ClientInputException}. Only loaded when sdk-commons is on the
 * classpath, so the handler works without it; no other class of sdk-web references sdk-commons.
 *
 * <p>The exception is answered when it is thrown directly or reached only through exceptions that
 * merely wrap it: Spring's {@code InvalidDataAccessApiUsageException}, {@code CompletionException},
 * {@code ExecutionException}, {@code UndeclaredThrowableException} and {@code ServletException}.
 * Any other exception on the way, or one annotated with {@link ResponseStatus}, means the server
 * decided the outcome, so it is not turned into a 400.
 */
final class ClientInputs {

  private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;
  private static final String KEY_PREFIX = "error.";
  private static final Set<String> WRAPPERS =
      Set.of(
          "org.springframework.dao.InvalidDataAccessApiUsageException",
          "java.util.concurrent.CompletionException",
          "java.util.concurrent.ExecutionException",
          "java.lang.reflect.UndeclaredThrowableException",
          "jakarta.servlet.ServletException");
  private static final ResourceBundleMessageSource DEFAULTS = defaults();

  private ClientInputs() {}

  /**
   * A 400 problem for a {@link ClientInputException} thrown directly or behind known wrappers. Its
   * detail is the application's bundle message {@code error.<code>}, else the SDK's default for the
   * codes it documents, else the exception message, safe by the exception's contract.
   */
  static Optional<ProblemSpec> problem(Throwable exception, int maxDepth) {
    Throwable current = exception;
    for (int depth = 0; current != null && depth < maxDepth; depth++) {
      if (current instanceof ClientInputException rejected) {
        return Optional.of(spec(rejected));
      }
      if (!isWrapper(current)) {
        return Optional.empty();
      }
      current = current.getCause();
    }
    return Optional.empty();
  }

  private static boolean isWrapper(Throwable throwable) {
    if (AnnotatedElementUtils.hasAnnotation(throwable.getClass(), ResponseStatus.class)) {
      return false;
    }
    for (Class<?> type = throwable.getClass(); type != null; type = type.getSuperclass()) {
      if (WRAPPERS.contains(type.getName())) {
        return true;
      }
    }
    return false;
  }

  private static ProblemSpec spec(ClientInputException rejected) {
    String code = rejected.code();
    String detail = sdkDefault(code).orElseGet(() -> message(rejected));
    return new ProblemSpec(STATUS, code, KEY_PREFIX + code, List.of(), detail, List.of());
  }

  private static String message(ClientInputException rejected) {
    return Objects.requireNonNullElse(rejected.getMessage(), StatusCodes.reasonPhrase(STATUS));
  }

  private static Optional<String> sdkDefault(String code) {
    Locale locale = LocaleContextHolder.getLocale();
    return Optional.ofNullable(DEFAULTS.getMessage(KEY_PREFIX + code, null, null, locale));
  }

  private static ResourceBundleMessageSource defaults() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("io/codehunters/web/errors/client-input");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
  }
}
