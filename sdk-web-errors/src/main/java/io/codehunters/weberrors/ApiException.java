package io.codehunters.weberrors;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unchecked exception carrying a stable {@link ErrorCode}. The SDK answers it with a problem detail
 * whose status, {@code code} and localized {@code detail} come from the code.
 *
 * <p>The exception message is the code alone, so logs never contain the arguments. Arguments fill
 * the placeholders of the localized detail and are therefore sent to the client: never pass secrets
 * or personal data.
 */
public class ApiException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Transient because nothing forces error codes or arguments to be serializable. The exception
   * refuses serialization instead of arriving with them missing.
   */
  private final transient ErrorCode errorCode;

  private final transient List<Object> arguments;

  /**
   * Creates an exception without message arguments.
   *
   * @param errorCode the error code
   */
  public ApiException(ErrorCode errorCode) {
    this(errorCode, List.of(), null);
  }

  /**
   * Creates an exception whose arguments fill the placeholders of the localized detail.
   *
   * @param errorCode the error code
   * @param arguments the message arguments, sent to the client inside the detail
   */
  public ApiException(ErrorCode errorCode, List<?> arguments) {
    this(errorCode, arguments, null);
  }

  /**
   * Creates an exception with arguments and a cause, kept for the server log only.
   *
   * @param errorCode the error code
   * @param arguments the message arguments, sent to the client inside the detail
   * @param cause the underlying failure, never sent to the client
   */
  public ApiException(ErrorCode errorCode, List<?> arguments, Throwable cause) {
    super(requireCode(errorCode).code(), cause);
    this.errorCode = errorCode;
    this.arguments = copy(arguments);
  }

  /**
   * The error code.
   *
   * @return the error code
   */
  public ErrorCode errorCode() {
    return errorCode;
  }

  /**
   * The message arguments, in placeholder order.
   *
   * @return an unmodifiable list that may contain {@code null}
   */
  public List<Object> arguments() {
    return arguments;
  }

  /**
   * Refuses serialization: the code and arguments are transient and would arrive missing.
   *
   * @param out the stream
   * @throws IOException always, as {@link NotSerializableException}
   */
  @Serial
  private void writeObject(ObjectOutputStream out) throws IOException {
    throw new NotSerializableException(ApiException.class.getName());
  }

  private static ErrorCode requireCode(ErrorCode errorCode) {
    if (errorCode == null) {
      throw new IllegalArgumentException("errorCode must not be null");
    }
    String code = errorCode.code();
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("errorCode must have a code");
    }
    return errorCode;
  }

  private static List<Object> copy(List<?> arguments) {
    if (arguments == null) {
      throw new IllegalArgumentException("arguments must not be null");
    }
    return Collections.unmodifiableList(new ArrayList<>(arguments));
  }
}
