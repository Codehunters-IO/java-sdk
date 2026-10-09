package io.codehunters.messaging;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A valid W3C {@code traceparent} header value ({@code <version>-<trace-id>-<span-id>-<flags>}),
 * the one check the outbox factory, the stores, the transport adapters and the traced publisher
 * share before storing, sending or continuing a stored trace context.
 *
 * <p>A value is valid when it is exactly 55 characters of lowercase hex fields: a version other
 * than {@code ff}, a 32-character trace id and a 16-character span id that are not all zeros, and
 * 2-character flags. Values of a future version that carry more than those four fields are rejected
 * rather than truncated.
 *
 * @param version the 2-character version
 * @param traceId the 32-character trace id
 * @param spanId the 16-character parent span id
 * @param flags the 2-character trace flags
 */
public record W3cTraceParent(String version, String traceId, String spanId, String flags) {

  private static final int LENGTH = 55;
  private static final String INVALID_VERSION = "ff";
  private static final Pattern FORMAT =
      Pattern.compile("([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})");
  private static final Pattern ALL_ZEROS = Pattern.compile("0+");

  /**
   * Validates the fields.
   *
   * @throws IllegalArgumentException when the fields do not form a valid {@code traceparent}
   */
  public W3cTraceParent {
    if (!isValid(String.join("-", version, traceId, spanId, flags))) {
      throw new IllegalArgumentException("not a valid W3C traceparent");
    }
  }

  /**
   * Parses a header value.
   *
   * @param value the header value, possibly null
   * @return the parsed context, or empty when the value is not a valid {@code traceparent}
   */
  public static Optional<W3cTraceParent> parse(String value) {
    if (!isValid(value)) {
      return Optional.empty();
    }
    Matcher fields = FORMAT.matcher(value);
    fields.matches();
    return Optional.of(
        new W3cTraceParent(fields.group(1), fields.group(2), fields.group(3), fields.group(4)));
  }

  /**
   * Whether a header value is a valid {@code traceparent}.
   *
   * @param value the header value, possibly null
   * @return {@code true} when it is valid
   */
  public static boolean isValid(String value) {
    if (value == null || value.length() != LENGTH) {
      return false;
    }
    Matcher fields = FORMAT.matcher(value);
    return fields.matches()
        && !INVALID_VERSION.equals(fields.group(1))
        && !ALL_ZEROS.matcher(fields.group(2)).matches()
        && !ALL_ZEROS.matcher(fields.group(3)).matches();
  }

  /**
   * The value itself when valid, otherwise null: what a store or a transport keeps of a value it
   * received, so a bad one is dropped instead of failing the write or the send.
   *
   * @param value the header value, possibly null
   * @return the value, or null when it is not a valid {@code traceparent}
   */
  public static String validOrNull(String value) {
    return isValid(value) ? value : null;
  }

  /**
   * Whether the sampled flag is set.
   *
   * @return {@code true} when the trace was sampled
   */
  public boolean sampled() {
    return (Integer.parseInt(flags, 16) & 1) == 1;
  }

  /**
   * The header value.
   *
   * @return {@code <version>-<trace-id>-<span-id>-<flags>}
   */
  public String value() {
    return String.join("-", version, traceId, spanId, flags);
  }
}
