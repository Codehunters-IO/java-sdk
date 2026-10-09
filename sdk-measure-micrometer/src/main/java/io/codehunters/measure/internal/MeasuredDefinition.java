package io.codehunters.measure.internal;

import io.codehunters.measure.Measured;
import io.micrometer.core.instrument.Tags;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The parsed form of a {@link Measured} annotation.
 *
 * @param annotation the annotation
 * @param name the timer name
 * @param staticTags the tags from {@link Measured#tags()}, blank values replaced by {@code none}
 * @param expressionTags the tags from {@link Measured#expressions()}
 * @param countCondition the parsed {@link Measured#countCondition()}, or {@code null} to always
 *     count
 */
public record MeasuredDefinition(
    Measured annotation,
    String name,
    Tags staticTags,
    List<ExpressionTag> expressionTags,
    MeasuredExpression countCondition) {

  /** The timer name of an annotation that names none. */
  public static final String DEFAULT_NAME = "method.measured";

  static final Set<String> RESERVED_KEYS = Set.of("class", "method", "exception", "outcome");

  private static final Pattern TAG_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");

  /**
   * Parses and validates an annotation.
   *
   * @param measured the annotation
   * @return the definition
   * @throws IllegalArgumentException if the tags are not key/value pairs, an expression is not
   *     {@code key=expression}, a tag key is blank, reserved, repeated or not a tag name, or the
   *     count value is not positive and finite
   */
  public static MeasuredDefinition of(Measured measured) {
    requireCountValue(measured.countValue());
    Set<String> keys = new HashSet<>();
    return new MeasuredDefinition(
        measured,
        measured.name().isBlank() ? DEFAULT_NAME : measured.name(),
        staticTags(measured.tags(), keys),
        expressionTags(measured.expressions(), keys),
        measured.countCondition().isBlank()
            ? null
            : MeasuredExpression.parse(measured.countCondition()));
  }

  private static Tags staticTags(String[] pairs, Set<String> keys) {
    if (pairs.length % 2 != 0) {
      throw new IllegalArgumentException(
          "tags must be key/value pairs, but has " + pairs.length + " entries");
    }
    Tags tags = Tags.empty();
    for (int i = 0; i < pairs.length; i += 2) {
      String value = pairs[i + 1].isBlank() ? MeasuredExpression.NONE : pairs[i + 1];
      tags = tags.and(requireKey(pairs[i], keys), value);
    }
    return tags;
  }

  private static List<ExpressionTag> expressionTags(String[] expressions, Set<String> keys) {
    List<ExpressionTag> tags = new ArrayList<>();
    for (String entry : expressions) {
      String[] keyValue = entry.split("=", 2);
      if (keyValue.length != 2 || keyValue[1].isBlank()) {
        throw new IllegalArgumentException("expression '" + entry + "' is not key=expression");
      }
      String key = requireKey(keyValue[0].trim(), keys);
      tags.add(new ExpressionTag(key, MeasuredExpression.parse(keyValue[1].trim())));
    }
    return List.copyOf(tags);
  }

  /**
   * Returns every tag key the timer of this definition carries, reserved ones included.
   *
   * @return the tag keys
   */
  public Set<String> tagKeys() {
    Set<String> keys = new TreeSet<>(RESERVED_KEYS);
    staticTags.forEach(tag -> keys.add(tag.getKey()));
    expressionTags.forEach(tag -> keys.add(tag.key()));
    return keys;
  }

  private static void requireCountValue(double countValue) {
    if (!(countValue > 0) || Double.isInfinite(countValue)) {
      throw new IllegalArgumentException(
          "countValue must be positive and finite, but is " + countValue);
    }
  }

  private static String requireKey(String key, Set<String> keys) {
    if (key.isBlank()) {
      throw new IllegalArgumentException("tag keys must not be blank");
    }
    if (!TAG_KEY.matcher(key).matches()) {
      throw new IllegalArgumentException("tag key '" + key + "' must match " + TAG_KEY.pattern());
    }
    if (RESERVED_KEYS.contains(key)) {
      throw new IllegalArgumentException("tag key '" + key + "' is reserved");
    }
    if (!keys.add(key)) {
      throw new IllegalArgumentException("tag key '" + key + "' is repeated");
    }
    return key;
  }

  /**
   * A tag whose value comes from a SpEL expression.
   *
   * @param key the tag key
   * @param expression the expression that yields the value
   */
  record ExpressionTag(String key, MeasuredExpression expression) {}
}
