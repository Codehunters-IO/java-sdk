package io.codehunters.tracing;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

/**
 * A W3C trace context: trace id, the current span id and the sampled flag. Ids are lowercase hex
 * and never all zeros, as the specification requires.
 *
 * @param traceId 32 hex characters identifying the whole trace
 * @param spanId 16 hex characters identifying the current span
 * @param sampled whether the trace is sampled
 */
public record TraceParent(String traceId, String spanId, boolean sampled) {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TRACE_ID_LENGTH = 32;
  private static final int SPAN_ID_LENGTH = 16;
  private static final String VERSION = "00";
  private static final String INVALID_VERSION = "ff";

  /** Rejects ids the W3C specification forbids. */
  public TraceParent {
    if (!isValidId(traceId, TRACE_ID_LENGTH) || !isValidId(spanId, SPAN_ID_LENGTH)) {
      throw new IllegalArgumentException("Invalid W3C trace or span id");
    }
  }

  /**
   * Parses a {@code traceparent} header.
   *
   * @param header the header value, possibly {@code null}
   * @return the context, or empty when the header is absent or invalid
   */
  public static Optional<TraceParent> parse(String header) {
    if (header == null) {
      return Optional.empty();
    }
    String[] fields = header.trim().split("-", -1);
    if (fields.length < 4 || !isHex(fields[0], 2) || INVALID_VERSION.equals(fields[0])) {
      return Optional.empty();
    }
    if (VERSION.equals(fields[0]) && fields.length != 4) {
      return Optional.empty();
    }
    return of(fields[1], fields[2], fields[3]);
  }

  /**
   * Builds a sampled context for a bare trace id, as 0.x producers sent, with a new span id.
   *
   * @param traceId the trace id, possibly {@code null}
   * @return the context, or empty when the trace id is not a valid W3C trace id
   */
  public static Optional<TraceParent> ofTraceId(String traceId) {
    return isValidId(traceId, TRACE_ID_LENGTH)
        ? Optional.of(new TraceParent(traceId, newSpanId(), true))
        : Optional.empty();
  }

  /**
   * Builds a context from its parts.
   *
   * @param traceId the trace id
   * @param spanId the span id
   * @param flags two hex characters; {@code null} means sampled
   * @return the context, or empty when any part is invalid
   */
  public static Optional<TraceParent> of(String traceId, String spanId, String flags) {
    if (!isValidId(traceId, TRACE_ID_LENGTH) || !isValidId(spanId, SPAN_ID_LENGTH)) {
      return Optional.empty();
    }
    if (flags != null && !isHex(flags, 2)) {
      return Optional.empty();
    }
    boolean sampled = flags == null || (Integer.parseInt(flags, 16) & 1) == 1;
    return Optional.of(new TraceParent(traceId, spanId, sampled));
  }

  /**
   * Starts a new, sampled trace.
   *
   * @return the context
   */
  public static TraceParent generate() {
    return new TraceParent(randomHex(TRACE_ID_LENGTH), newSpanId(), true);
  }

  /**
   * Starts a span within this trace, keeping its sampled flag.
   *
   * @return the child context
   */
  public TraceParent child() {
    return new TraceParent(traceId, newSpanId(), sampled);
  }

  /**
   * The trace flags field.
   *
   * @return {@code 01} when sampled, otherwise {@code 00}
   */
  public String flags() {
    return sampled ? "01" : "00";
  }

  /**
   * Formats the {@code traceparent} header value.
   *
   * @return {@code 00-<trace-id>-<span-id>-<flags>}
   */
  public String format() {
    return VERSION + "-" + traceId + "-" + spanId + "-" + flags();
  }

  private static String newSpanId() {
    return randomHex(SPAN_ID_LENGTH);
  }

  private static String randomHex(int length) {
    String hex;
    do {
      byte[] bytes = new byte[length / 2];
      RANDOM.nextBytes(bytes);
      hex = HexFormat.of().formatHex(bytes);
    } while (isAllZeros(hex));
    return hex;
  }

  private static boolean isValidId(String value, int length) {
    return isHex(value, length) && !isAllZeros(value);
  }

  private static boolean isHex(String value, int length) {
    return value != null
        && value.length() == length
        && value.chars().allMatch(TraceParent::isLowerHex);
  }

  private static boolean isLowerHex(int c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
  }

  private static boolean isAllZeros(String value) {
    return value.chars().allMatch(c -> c == '0');
  }
}
