package io.codehunters.errors;

import java.io.Serial;
import java.util.regex.Pattern;

/**
 * The client sent input this code cannot accept: a sort or search the API does not allow, an upload
 * the service refuses, a value outside the accepted range. A web layer answers it with 400 and the
 * {@link #code()}; sdk-web does so out of the box, also when the exception arrives as the cause of
 * another one.
 *
 * <p>It is an {@link IllegalArgumentException}, so code that already catches those keeps working.
 * Programming errors (a {@code null} argument, a misconfigured whitelist) stay plain {@code
 * IllegalArgumentException}s: they are the server's fault, not the client's.
 *
 * <p><b>Message contract.</b> The message may be sent to the client as the problem detail, so it
 * must describe the rule that was broken and never repeat the client's input or any other secret,
 * personal or internal data. "sort property is not allowed; allowed: created, name" is fine;
 * "cannot sort by passwordHash" is not. The same contract makes the message safe to log.
 *
 * <p>The {@link #code()} is part of the API contract: once released, keep it stable. It is upper
 * snake case, matching {@code [A-Z][A-Z0-9_]*}, such as {@code INVALID_SORT}.
 */
public class ClientInputException extends IllegalArgumentException {

  @Serial private static final long serialVersionUID = 1L;

  private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]*");

  /** The stable error code. */
  private final String code;

  /**
   * Creates the exception.
   *
   * @param code the stable error code, matching {@code [A-Z][A-Z0-9_]*}
   * @param message what is wrong, without the client's input
   * @throws IllegalArgumentException if the code does not match or the message is blank
   */
  public ClientInputException(String code, String message) {
    this(code, message, null);
  }

  /**
   * Creates the exception with the failure behind it, kept for the server log only.
   *
   * @param code the stable error code, matching {@code [A-Z][A-Z0-9_]*}
   * @param message what is wrong, without the client's input
   * @param cause the underlying failure, may be {@code null}
   * @throws IllegalArgumentException if the code does not match or the message is blank
   */
  public ClientInputException(String code, String message, Throwable cause) {
    super(requireMessage(message), cause);
    this.code = requireCode(code);
  }

  /**
   * The stable error code, such as {@code INVALID_SORT}.
   *
   * @return the code
   */
  public String code() {
    return code;
  }

  private static String requireCode(String code) {
    if (code == null || !CODE.matcher(code).matches()) {
      throw new IllegalArgumentException("code must match " + CODE.pattern());
    }
    return code;
  }

  private static String requireMessage(String message) {
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("message must not be blank");
    }
    return message;
  }
}
