package io.codehunters.measure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;

/**
 * Records a timer, or a long-task timer, and the optional counter of one measured call.
 *
 * <p>Meter names: the timer is the definition's name, tagged {@code class}, {@code method}, {@code
 * exception} and {@code outcome} plus the annotation's tags; the long-task timer is {@code
 * <name>.active} and the counter {@code <name>.counted}, both without {@code exception} and {@code
 * outcome}.
 */
final class RecordingMeasurement implements Measurement {

  private static final Logger log = LoggerFactory.getLogger(RecordingMeasurement.class);

  private final Call call;
  private final Timer.Sample sample;
  private final LongTaskTimer.Sample activeTask;

  private RecordingMeasurement(Call call, Timer.Sample sample, LongTaskTimer.Sample activeTask) {
    this.call = call;
    this.sample = sample;
    this.activeTask = activeTask;
  }

  static Measurement start(Call call) {
    if (call.definition().annotation().longTask()) {
      LongTaskTimer active =
          LongTaskTimer.builder(call.definition().name() + ".active")
              .description("@Measured calls in progress")
              .tags(call.tags())
              .register(call.registry());
      return new RecordingMeasurement(call, null, active.start());
    }
    return new RecordingMeasurement(call, Timer.start(call.registry()), null);
  }

  @Override
  public void finished(Object result, Throwable failure) {
    try {
      stop(failure);
      countIfDue(result, failure);
    } catch (RuntimeException ex) {
      log.warn(
          "Could not record @Measured meters of '{}': {}",
          call.definition().name(),
          ex.getClass().getName());
    }
  }

  private void stop(Throwable failure) {
    if (activeTask != null) {
      activeTask.stop();
      return;
    }
    sample.stop(
        Timer.builder(call.definition().name())
            .description("Duration of @Measured calls")
            .tags(call.tags())
            .tags(outcomeTags(failure))
            .register(call.registry()));
  }

  private Tags outcomeTags(Throwable failure) {
    if (failure == null) {
      return Tags.of("exception", MeasuredExpression.NONE, "outcome", "SUCCESS");
    }
    boolean recorded = call.definition().annotation().recordExceptions();
    return Tags.of(
        "exception",
        recorded ? exceptionName(failure) : MeasuredExpression.NONE,
        "outcome",
        "ERROR");
  }

  private static String exceptionName(Throwable failure) {
    String simpleName = failure.getClass().getSimpleName();
    return simpleName.isEmpty() ? failure.getClass().getName() : simpleName;
  }

  private void countIfDue(Object result, Throwable failure) {
    Measured measured = call.definition().annotation();
    if (!measured.count() || failure != null || !conditionHolds(result)) {
      return;
    }
    Counter.builder(call.definition().name() + ".counted")
        .description("@Measured calls that met their count condition")
        .tags(call.tags())
        .register(call.registry())
        .increment(measured.countValue());
  }

  private boolean conditionHolds(Object result) {
    MeasuredExpression condition = call.definition().countCondition();
    if (condition == null) {
      return true;
    }
    call.context().setVariable("result", result);
    return condition.holds(call.context());
  }

  /**
   * What a measurement needs to know about its call.
   *
   * @param registry the registry the meters go to
   * @param definition the parsed annotation
   * @param tags the class, method and annotation tags
   * @param context the variables of the call, for the count condition
   */
  record Call(
      MeterRegistry registry,
      MeasuredDefinition definition,
      Tags tags,
      EvaluationContext context) {}
}
