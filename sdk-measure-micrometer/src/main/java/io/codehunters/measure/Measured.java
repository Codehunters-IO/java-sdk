package io.codehunters.measure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Measures a method, or every method of a class, with Micrometer.
 *
 * <p>Each call records the timer {@link #name()} tagged {@code class}, {@code method}, {@code
 * exception} ({@code none} on success) and {@code outcome} ({@code SUCCESS} or {@code ERROR}), plus
 * the {@link #tags()} and {@link #expressions()} tags. Every call of a method records the same tag
 * keys. On a class, the annotation applies to each method without its own {@code @Measured}.
 *
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Measured {

  /**
   * Timer name. When blank, the timer is {@code method.measured} and the {@code class} and {@code
   * method} tags tell the methods apart.
   *
   * @return the timer name
   */
  String name() default "";

  /**
   * Static tags as alternating key/value entries. A blank value is recorded as {@code none}. An odd
   * number of entries, or a blank, reserved ({@code class}, {@code method}, {@code exception},
   * {@code outcome}) or repeated key, fails application startup.
   *
   * @return the static tags
   */
  String[] tags() default {};

  /**
   * Whether the {@code exception} tag names the exception a failed call threw. When {@code false}
   * it is {@code none}; the {@code outcome} tag is still {@code ERROR}.
   *
   * @return {@code true} to tag the exception's simple class name
   */
  boolean recordExceptions() default true;

  /**
   * Whether to track calls in progress with the long-task timer {@code <name>.active} instead of
   * the timer.
   *
   * @return {@code true} to use a long-task timer
   */
  boolean longTask() default false;

  /**
   * Tags whose values come from SpEL expressions over the arguments, as {@code key=expression},
   * e.g. {@code "customer=#command.customerId"}. A {@code null} or blank value is recorded as
   * {@code none}, and an expression that fails as {@code unknown}. Each tag takes at most {@code
   * codehunters.measure.max-tag-values} distinct values per timer; further values are recorded as
   * {@code other}.
   *
   * @return the SpEL tag expressions
   */
  String[] expressions() default {};

  /**
   * Whether to increment the counter {@code <name>.counted} after each successful call.
   *
   * @return {@code true} to enable the counter
   */
  boolean count() default false;

  /**
   * SpEL condition that must evaluate to {@code true} for the counter to increment. It sees the
   * arguments and {@code #result}. When blank the counter always increments; when it fails to
   * evaluate, the counter does not.
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
