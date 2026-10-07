package io.codehunters.commons.masking;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Masks sensitive values for logs, messages and UIs. Input is first composed (NFC), so an accented
 * letter counts as one character and is never split from its accent. Every method returns a new
 * string with as many code points as the composed input and never echoes the input in an exception
 * message.
 *
 * <p>When the characters to keep would cover the whole value, the whole value is masked: a mask
 * never reveals everything it was asked to hide.
 */
public final class Masking {

  /** Mask character used when none is given. */
  public static final char DEFAULT_MASK = '*';

  private static final Pattern CARD_SHAPE = Pattern.compile("\\d+(?:[ -]\\d+)*");
  private static final Pattern CARD_SEPARATORS = Pattern.compile("[ -]");
  private static final Pattern CARD_DIGITS = Pattern.compile("\\d{12,19}");
  private static final int CARD_VISIBLE_DIGITS = 4;

  private Masking() {}

  /**
   * Keeps the first {@code first} and the last {@code last} code points and masks the rest.
   *
   * @param text the value to mask
   * @param first how many leading code points stay visible
   * @param last how many trailing code points stay visible
   * @return the masked value, fully masked when {@code first + last} covers the whole value
   * @throws IllegalArgumentException if {@code text} is null or a count is negative
   */
  public static String keep(String text, int first, int last) {
    return keep(text, first, last, DEFAULT_MASK);
  }

  /**
   * Same as {@link #keep(String, int, int)} with a custom mask character.
   *
   * @param text the value to mask
   * @param first how many leading code points stay visible
   * @param last how many trailing code points stay visible
   * @param mask the character that replaces hidden code points, not a surrogate
   * @return the masked value
   */
  public static String keep(String text, int first, int last, char mask) {
    requireText(text);
    requireNotNegative(first, "first");
    requireNotNegative(last, "last");
    if (Character.isSurrogate(mask)) {
      throw new IllegalArgumentException("mask must not be a surrogate");
    }
    int[] codePoints = Normalizer.normalize(text, Normalizer.Form.NFC).codePoints().toArray();
    if ((long) first + last >= codePoints.length) {
      return String.valueOf(mask).repeat(codePoints.length);
    }
    return render(codePoints, first, codePoints.length - last, mask);
  }

  /**
   * Keeps the first {@code count} code points visible.
   *
   * @param text the value to mask
   * @param count how many leading code points stay visible
   * @return the masked value
   */
  public static String keepFirst(String text, int count) {
    return keep(text, count, 0);
  }

  /**
   * Keeps the last {@code count} code points visible.
   *
   * @param text the value to mask
   * @param count how many trailing code points stay visible
   * @return the masked value
   */
  public static String keepLast(String text, int count) {
    return keep(text, 0, count);
  }

  /**
   * Masks the local part of an e-mail address except its first character; the domain stays visible.
   * A value that is not {@code local@domain} with exactly one {@code @} is fully masked.
   *
   * @param email the address to mask
   * @return the masked address
   */
  public static String email(String email) {
    requireText(email);
    String composed = Normalizer.normalize(email, Normalizer.Form.NFC);
    int at = composed.indexOf('@');
    boolean wellFormed = at > 0 && at == composed.lastIndexOf('@') && at < composed.length() - 1;
    if (!wellFormed) {
      return keep(composed, 0, 0);
    }
    return keepFirst(composed.substring(0, at), 1) + composed.substring(at);
  }

  /**
   * Masks a payment card number (PAN), keeping only its last four digits. Spaces and dashes are
   * removed from the result.
   *
   * @param pan the card number, 12 to 19 digits, optionally grouped with single spaces or dashes
   *     between digits
   * @return the masked digits
   * @throws IllegalArgumentException if the value is not a card number
   */
  public static String pan(String pan) {
    requireText(pan);
    String digits = CARD_SEPARATORS.matcher(pan).replaceAll("");
    if (!CARD_SHAPE.matcher(pan).matches() || !CARD_DIGITS.matcher(digits).matches()) {
      throw new IllegalArgumentException("value is not a card number of 12 to 19 digits");
    }
    return keepLast(digits, CARD_VISIBLE_DIGITS);
  }

  private static String render(int[] codePoints, int visibleUntil, int visibleFrom, char mask) {
    var builder = new StringBuilder(codePoints.length);
    for (int i = 0; i < codePoints.length; i++) {
      boolean visible = i < visibleUntil || i >= visibleFrom;
      builder.appendCodePoint(visible ? codePoints[i] : mask);
    }
    return builder.toString();
  }

  private static void requireText(String text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
  }

  private static void requireNotNegative(int count, String name) {
    if (count < 0) {
      throw new IllegalArgumentException(name + " must not be negative");
    }
  }
}
