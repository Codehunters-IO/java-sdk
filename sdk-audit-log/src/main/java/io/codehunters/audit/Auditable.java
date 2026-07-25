package io.codehunters.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for audit logging. Applicable to RestControllers and UseCases.
 *
 * <p>SpEL expressions can reference method parameters by name (#paramName), by position (#p0, #a0,
 * #arg0), and the return value via #result.
 *
 * <p>Example:
 *
 * <pre>
 * {@code @Auditable(
 *     entityType = "ORDER",
 *     action = "CREATE",
 *     entityId = "#result.id",
 *     sourceService = "order-service"
 * )}
 * public Order createOrder(CreateOrderCommand cmd) { ... }
 * </pre>
 *
 * @since 1.0.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

  /**
   * Entity type being audited.
   *
   * @return the entity type (e.g. ORDER, PAYMENT, USER)
   */
  String entityType();

  /**
   * Action performed.
   *
   * @return the action (e.g. CREATE, UPDATE, DELETE, READ)
   */
  String action();

  /**
   * Entity identifier.
   *
   * @return a SpEL expression resolving the entity ID
   */
  String entityId() default "";

  /**
   * Actor identifier.
   *
   * @return a SpEL expression resolving the actor ID; falls back to {@link AuditContextProvider}
   */
  String actorId() default "";

  /**
   * Actor type.
   *
   * @return the actor type (e.g. USER, SYSTEM, SERVICE); falls back to {@link AuditContextProvider}
   */
  String actorType() default "";

  /**
   * Originating service.
   *
   * @return the service name originating the audit event
   */
  String sourceService() default "";

  /**
   * Correlation identifier.
   *
   * @return a SpEL expression resolving a correlation ID
   */
  String correlationId() default "";

  /**
   * SpEL expressions to build the payload map. Format: {@code {"key=#expression",
   * "amount=#cmd.amount"}}.
   *
   * @return the payload SpEL expressions
   */
  String[] payload() default {};
}
