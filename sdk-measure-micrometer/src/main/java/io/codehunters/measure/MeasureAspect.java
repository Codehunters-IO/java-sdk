package io.codehunters.measure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * Aspect for measuring methods
 *
 * @since 1.0.0
 * @author anresmontoyat@gmail.com
 */
@Aspect
public class MeasureAspect {

  private final Logger log = LoggerFactory.getLogger(MeasureAspect.class);

  private final MeterRegistry registry;
  private final ExpressionParser expressionParser;
  private final ParameterNameDiscoverer parameterNameDiscoverer;

  /**
   * Creates the aspect.
   *
   * @param registry the Micrometer registry timers and counters are registered against
   */
  public MeasureAspect(MeterRegistry registry) {
    this.registry = registry;
    this.expressionParser = new SpelExpressionParser();
    this.parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
  }

  /**
   * Times (and optionally counts) a method annotated with {@link Measured}.
   *
   * @param pjp the intercepted join point
   * @param measured the annotation on the intercepted method
   * @return the value returned by the intercepted method
   * @throws Throwable if the intercepted method throws
   */
  @Around("@annotation(measured)")
  public Object aroundAnnotated(ProceedingJoinPoint pjp, Measured measured) throws Throwable {
    if (log.isDebugEnabled()) log.debug("Measuring method: {}", pjp.getSignature().toShortString());
    return time(pjp, measured);
  }

  private Object time(ProceedingJoinPoint pjp, Measured measured) throws Throwable {
    MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
    Method method = methodSignature.getMethod();
    String metricName = resolveMetricName(measured, method);

    if (log.isDebugEnabled()) log.debug("Metric name resolved to: {}", metricName);

    EvaluationContext evaluationContext = buildEvaluationContext(pjp, method);
    List<String> tags = resolveTags(measured, evaluationContext);
    Iterable<Tag> micrometerTags = toTags(tags);

    if (log.isDebugEnabled()) log.debug("Tags for metric '{}': {}", metricName, tags);

    boolean useLongTask = measured.longTask();

    Timer.Sample timerSample = null;
    Timer timer = null;
    LongTaskTimer.Sample longSample = null;

    if (useLongTask) {
      if (log.isDebugEnabled()) log.debug("Starting long-task timer for '{}'", metricName);
      LongTaskTimer ltt =
          LongTaskTimer.builder(String.format("%s.long-task", metricName))
              .description("Long task execution time")
              .tags(micrometerTags)
              .register(registry);
      longSample = ltt.start();
    } else {
      if (log.isDebugEnabled()) log.debug("Starting timer for '{}'", metricName);
      timerSample = Timer.start(registry);
      timer =
          Timer.builder(metricName)
              .description("Execution time")
              .tags(micrometerTags)
              .register(registry);
    }

    try {
      Object result = pjp.proceed();
      incrementCountIfEnabled(measured, metricName, micrometerTags, evaluationContext, result);
      return result;
    } catch (Throwable ex) {
      if (log.isDebugEnabled())
        log.debug("Exception caught in '{}': {}", metricName, ex.getClass().getSimpleName());
      if (measured.recordExceptions()) {
        if (log.isDebugEnabled())
          log.debug("Recording exception counter for '{}.errors'", metricName);
        Counter.builder(String.format("%s.errors", metricName))
            .tags(micrometerTags)
            .tag("exception", ex.getClass().getSimpleName())
            .register(registry)
            .increment();
      }
      throw ex;

    } finally {
      if (useLongTask && longSample != null) {
        longSample.stop();
        if (log.isDebugEnabled()) log.debug("Long-task timer stopped for '{}'", metricName);
      } else if (timerSample != null && timer != null) {
        timerSample.stop(timer);
        if (log.isDebugEnabled()) log.debug("Timer stopped for '{}'", metricName);
      }
    }
  }

  private String resolveMetricName(Measured measured, Method method) {
    return measured.name() == null || measured.name().isBlank()
        ? method.getDeclaringClass().getSimpleName() + "." + method.getName()
        : measured.name();
  }

  private EvaluationContext buildEvaluationContext(ProceedingJoinPoint pjp, Method method) {
    Object[] args = pjp.getArgs();
    String[] paramNames = parameterNameDiscoverer.getParameterNames(method);

    if (paramNames == null || paramNames.length == 0) {
      paramNames = new String[args.length];
      for (int i = 0; i < args.length; i++) paramNames[i] = "arg" + i;
    }

    EvaluationContext ctx = new StandardEvaluationContext();
    for (int i = 0; i < args.length; i++) {
      ctx.setVariable("p" + i, args[i]);
      ctx.setVariable("a" + i, args[i]);
      ctx.setVariable("arg" + i, args[i]);
    }

    for (int i = 0; i < paramNames.length; i++) {
      ctx.setVariable(paramNames[i], args[i]);
    }

    return ctx;
  }

  private List<String> resolveTags(Measured measured, EvaluationContext ctx) {
    List<String> tags = new ArrayList<>(Arrays.asList(measured.tags()));

    if (measured.expressions() == null || measured.expressions().length == 0) {
      return tags;
    }

    for (String expression : measured.expressions()) {
      String[] keyValue = expression.split("=", 2);
      if (keyValue.length == 2) {
        String key = keyValue[0].trim();
        String value =
            expressionParser.parseExpression(keyValue[1].trim()).getValue(ctx, String.class);
        if (value != null && !value.isBlank()) {
          if (log.isDebugEnabled()) log.debug("Expression tag resolved: {}={}", key, value);
          tags.add(key);
          tags.add(value);
        }
      }
    }

    return tags;
  }

  private void incrementCountIfEnabled(
      Measured measured,
      String metricName,
      Iterable<Tag> tags,
      EvaluationContext ctx,
      Object result) {
    if (!measured.count()) return;

    ctx.setVariable("result", result);

    String condition = measured.countCondition();
    if (condition != null && !condition.isBlank()) {
      Boolean shouldCount =
          expressionParser.parseExpression(condition).getValue(ctx, Boolean.class);
      if (!Boolean.TRUE.equals(shouldCount)) {
        if (log.isDebugEnabled())
          log.debug("Count condition evaluated to false for '{}'", metricName);
        return;
      }
    }

    String counterName = String.format("%s.count", metricName);

    Counter.builder(counterName)
        .description("Execution count")
        .tags(tags)
        .register(registry)
        .increment(measured.countValue());

    if (log.isDebugEnabled())
      log.debug("Counter '{}' incremented by {}", counterName, measured.countValue());
  }

  private Iterable<Tag> toTags(List<String> kv) {
    List<Tag> out = new ArrayList<>();
    for (int i = 0; i + 1 < kv.size(); i += 2) {
      String k = kv.get(i), v = kv.get(i + 1);
      if (k != null && v != null && !k.isBlank() && !v.isBlank()) out.add(Tag.of(k, v));
    }
    return out;
  }
}
