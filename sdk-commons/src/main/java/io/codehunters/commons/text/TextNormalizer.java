package io.codehunters.commons.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns free text into a stable, lower-case token made of letters, digits and single underscores:
 * suitable for slugs, keys and file names.
 */
public final class TextNormalizer {

  /** Separator that replaces spaces and symbols. */
  public static final char SEPARATOR = '_';

  private static final Pattern LATIN_DIACRITICS =
      Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
  private static final Pattern SEPARATOR_RUNS = Pattern.compile(SEPARATOR + "{2,}");
  private static final String DROPPED = ".\"'";
  private static final Map<Integer, String> NON_DECOMPOSING =
      Map.of(
          (int) 'ł', "l",
          (int) 'ß', "ss",
          (int) 'æ', "ae",
          (int) 'ø', "o",
          (int) 'œ', "oe",
          (int) 'ð', "d",
          (int) 'đ', "d",
          (int) 'ɖ', "d",
          (int) 'þ', "th");

  private TextNormalizer() {}

  /**
   * Simplifies {@code text}:
   *
   * <ol>
   *   <li>lower-cases it with {@link Locale#ROOT};
   *   <li>removes Latin diacritics ({@code é} becomes {@code e}) and spells out letters that do not
   *       decompose ({@code ß}, {@code æ}, {@code ø}, {@code œ}, {@code ł}, {@code ð}, {@code đ},
   *       {@code þ});
   *   <li>drops dots and quotes;
   *   <li>turns every other character that is not a letter, digit or combining mark into a single
   *       {@code _}, trimmed at both ends.
   * </ol>
   *
   * <p>Letters of any script are kept. The result is empty when nothing survives.
   *
   * @param text the text to simplify
   * @return the simplified token, never null
   * @throws IllegalArgumentException if {@code text} is null
   */
  public static String simplify(String text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
    String plain =
        Normalizer.normalize(
            LATIN_DIACRITICS.matcher(decomposed).replaceAll(""), Normalizer.Form.NFC);
    String mapped = mapCodePoints(plain);
    return trimSeparators(SEPARATOR_RUNS.matcher(mapped).replaceAll(String.valueOf(SEPARATOR)));
  }

  private static String mapCodePoints(String text) {
    var builder = new StringBuilder(text.length());
    text.codePoints().forEach(codePoint -> builder.append(map(codePoint)));
    return builder.toString();
  }

  private static String map(int codePoint) {
    if (DROPPED.indexOf(codePoint) >= 0) {
      return "";
    }
    if (Character.isLetterOrDigit(codePoint) || isMark(codePoint)) {
      return NON_DECOMPOSING.getOrDefault(codePoint, Character.toString(codePoint));
    }
    return String.valueOf(SEPARATOR);
  }

  private static boolean isMark(int codePoint) {
    int type = Character.getType(codePoint);
    return type == Character.NON_SPACING_MARK
        || type == Character.COMBINING_SPACING_MARK
        || type == Character.ENCLOSING_MARK;
  }

  private static String trimSeparators(String text) {
    int start = 0;
    int end = text.length();
    while (start < end && text.charAt(start) == SEPARATOR) {
      start++;
    }
    while (end > start && text.charAt(end - 1) == SEPARATOR) {
      end--;
    }
    return text.substring(start, end);
  }
}
