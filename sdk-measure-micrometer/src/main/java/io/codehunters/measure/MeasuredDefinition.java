package io.codehunters.measure;

import io.micrometer.core.instrument.Tags;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
record MeasuredDefinition(
    Measured annotation,
    String name,
    Tags staticTags,
    List<ExpressionTag> expressionTags,
    MeasuredExpression countCondition) {

  static final String DEFAULT_NAME = "method.measured";
  static final Set<String> RESERVED_KEYS = Set.of("class", "method", "exception", "outcome");

  /**
   * Parses and validates an annotation.
   *
   * @param measured the annotation
   * @return the definition
   * @throws IllegalArgumentException if the tags are not key/value pairs, an expression is not
   *     {@code key=expression}, or a tag key is blank, reserved or repeated
   */
  static MeasuredDefinition of(Measured measured) {
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

  private static String requireKey(String key, Set<String> keys) {
    if (key.isBlank()) {
      throw new IllegalArgumentException("tag keys must not be blank");
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
