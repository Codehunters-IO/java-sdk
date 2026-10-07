package io.codehunters.measure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * Records Micrometer meters for methods annotated with {@link Measured}, or declared in a class
 * annotated with it. A method annotation takes precedence over the class annotation.
 *
 * <p>Measuring never changes the call: the method's result or exception reaches the caller as is,
 * and a failure to evaluate an expression or to register a meter is logged and skipped. A method
 * that returns a {@link CompletionStage} is measured until the stage completes, and the stage is
 * returned unchanged.
 *
 * <p>Expressions run in a {@link SimpleEvaluationContext} with read access to properties and
 * instance methods, and no access to types, constructors or beans. They see the arguments by name
 * ({@code #orderId}) and position ({@code #p0}, {@code #a0}, {@code #arg0}); a count condition also
 * sees {@code #result}. Each annotation is parsed once.
 *
 * @since 1.0.0
 */
@Aspect
public class MeasureAspect {

  /** Default cap on the distinct values of each expression tag. */
  public static final int DEFAULT_MAX_TAG_VALUES = 100;

  private static final Logger log = LoggerFactory.getLogger(MeasureAspect.class);

  private final MeterRegistry registry;
  private final TagCardinalityLimiter limiter;
  private final Map<Measured, MeasuredDefinition> definitions = new ConcurrentHashMap<>();
  private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

  /**
   * Creates the aspect with {@value #DEFAULT_MAX_TAG_VALUES} values per expression tag.
   *
   * @param registry the registry meters are registered against
   */
  public MeasureAspect(MeterRegistry registry) {
    this(registry, DEFAULT_MAX_TAG_VALUES);
  }

  /**
   * Creates the aspect.
   *
   * @param registry the registry meters are registered against
   * @param maxTagValues the distinct values each expression tag may take on a meter before new
   *     values are recorded as {@code other}
   * @throws IllegalArgumentException if {@code maxTagValues} is less than 1
   * @since 3.0.0
   */
  public MeasureAspect(MeterRegistry registry, int maxTagValues) {
    this.registry = registry;
    this.limiter = new TagCardinalityLimiter(maxTagValues);
  }

  /**
   * Measures a method annotated with {@link Measured}.
   *
   * @param pjp the intercepted join point
   * @param measured the annotation on the method
   * @return the value returned by the method
   * @throws Throwable if the method throws
   */
  @Around("@annotation(measured)")
  public Object measureMethod(ProceedingJoinPoint pjp, Measured measured) throws Throwable {
    return measure(pjp, measured);
  }

  /**
   * Measures a method of a class annotated with {@link Measured}, unless the method has its own
   * annotation.
   *
   * @param pjp the intercepted join point
   * @param measured the annotation on the class
   * @return the value returned by the method
   * @throws Throwable if the method throws
   * @since 3.0.0
   */
  @Around("@within(measured) && !@annotation(io.codehunters.measure.Measured)")
  public Object measureClass(ProceedingJoinPoint pjp, Measured measured) throws Throwable {
    return measure(pjp, measured);
  }

  private Object measure(ProceedingJoinPoint pjp, Measured measured) throws Throwable {
    Measurement measurement = start(pjp, measured);
    Object result;
    try {
      result = pjp.proceed();
    } catch (Throwable ex) {
      measurement.finished(null, ex);
      throw ex;
    }
    if (result instanceof CompletionStage<?> stage) {
      stage.whenComplete((value, failure) -> measurement.finished(value, unwrap(failure)));
      return result;
    }
    measurement.finished(result, null);
    return result;
  }

  private Measurement start(ProceedingJoinPoint pjp, Measured measured) {
    try {
      MeasuredDefinition definition = definitions.computeIfAbsent(measured, MeasuredDefinition::of);
      Method method = ((MethodSignature) pjp.getSignature()).getMethod();
      EvaluationContext context = context(method, pjp.getArgs());
      Tags tags =
          Tags.of("class", pjp.getStaticPart().getSignature().getDeclaringTypeName())
              .and("method", method.getName())
              .and(definition.staticTags())
              .and(expressionTags(definition, context));
      return RecordingMeasurement.start(
          new RecordingMeasurement.Call(registry, definition, tags, context));
    } catch (RuntimeException ex) {
      log.warn(
          "Could not start @Measured meters of {}: {}",
          pjp.getSignature().toShortString(),
          ex.getClass().getName());
      return Measurement.NONE;
    }
  }

  private Tags expressionTags(MeasuredDefinition definition, EvaluationContext context) {
    Tags tags = Tags.empty();
    for (MeasuredDefinition.ExpressionTag tag : definition.expressionTags()) {
      String value = tag.expression().tagValue(context);
      tags = tags.and(tag.key(), limiter.limit(definition.name(), tag.key(), value));
    }
    return tags;
  }

  private EvaluationContext context(Method method, Object[] args) {
    EvaluationContext context =
        SimpleEvaluationContext.forReadOnlyDataBinding().withInstanceMethods().build();
    String[] names = parameterNames.getParameterNames(method);
    for (int i = 0; i < args.length; i++) {
      context.setVariable("p" + i, args[i]);
      context.setVariable("a" + i, args[i]);
      context.setVariable("arg" + i, args[i]);
      if (names != null && i < names.length) {
        context.setVariable(names[i], args[i]);
      }
    }
    return context;
  }

  private static Throwable unwrap(Throwable failure) {
    if (failure instanceof CompletionException && failure.getCause() != null) {
      return failure.getCause();
    }
    return failure;
  }
}
