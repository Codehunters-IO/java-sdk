package io.codehunters.measure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for measure methods
 *
 * @since 1.0.0
 * @author anresmontoyat@gmail.com
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Measured {

  /**
   * Metric name. When blank, defaults to {@code SimpleClassName.methodName}.
   *
   * @return the metric name
   */
  String name() default "";

  /**
   * Static tags as alternating key/value entries.
   *
   * @return the static tags
   */
  String[] tags() default {};

  /**
   * Whether to record an error counter when the method throws.
   *
   * @return {@code true} to record exceptions
   */
  boolean recordExceptions() default true;

  /**
   * Whether to use a {@code LongTaskTimer} instead of a regular {@code Timer}.
   *
   * @return {@code true} to use a long-task timer
   */
  boolean longTask() default false;

  /**
   * SpEL expressions extracting dynamic tags from parameters. Format: {@code {"key1=#param1.prop",
   * "key2=#param2.other"}}.
   *
   * @return the SpEL tag expressions
   */
  String[] expressions() default {};

  /**
   * Enables a counter incremented after successful execution.
   *
   * @return {@code true} to enable the counter
   */
  boolean count() default false;

  /**
   * SpEL expression that must evaluate to {@code true} for the counter to increment. Has access to
   * {@code #result} and the method parameters; always increments when blank.
   *
   * @return the count-condition SpEL expression
   */
  String countCondition() default "";

  /**
   * Amount to increment the counter by; only applies when {@link #count()} is {@code true}.
   *
   * @return the counter increment value
   */
  double countValue() default 1.0;
}
