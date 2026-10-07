package io.codehunters.geo;

/**
 * Opaque reference to the scope whose zones a point is checked against, such as a campaign, store
 * or delivery-area id. The SDK never interprets it; the zone source or the stored procedure does.
 *
 * @param value the reference, 1 to {@value #MAX_LENGTH} characters, not blank, without control or
 *     format characters
 */
public record ZoneRef(String value) {

  /** Longest accepted reference. */
  public static final int MAX_LENGTH = 128;

  /**
   * Validates the reference. Messages never repeat the value.
   *
   * @throws IllegalArgumentException if the value is blank, too long or has invisible characters
   */
  public ZoneRef {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Zone reference must not be blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("Zone reference must have at most 128 characters");
    }
    if (value.codePoints().anyMatch(ZoneRef::isInvisible)) {
      throw new IllegalArgumentException(
          "Zone reference must not contain control or format characters");
    }
  }

  private static boolean isInvisible(int codePoint) {
    int type = Character.getType(codePoint);
    return type == Character.CONTROL || type == Character.FORMAT;
  }
}
