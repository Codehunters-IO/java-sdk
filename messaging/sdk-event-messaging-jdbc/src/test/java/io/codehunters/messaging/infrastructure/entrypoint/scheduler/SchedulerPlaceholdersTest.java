package io.codehunters.messaging.infrastructure.entrypoint.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class SchedulerPlaceholdersTest {

  @Test
  void should_read_dispatch_schedule_from_codehunters_prefix_when_scheduler_declared()
      throws NoSuchMethodException {
    Method method = OutboxScheduler.class.getMethod("processOutboxMessages");
    Scheduled scheduled = method.getAnnotation(Scheduled.class);
    SchedulerLock lock = method.getAnnotation(SchedulerLock.class);

    assertThat(scheduled.fixedDelayString())
        .isEqualTo("${codehunters.outbox.task.fixed-delay:5000}");
    assertThat(lock.name())
        .isEqualTo(
            "${codehunters.outbox.task.lock-name:outbox-${spring.application.name:unknown}}");
    assertThat(lock.lockAtMostFor()).isEqualTo("${codehunters.outbox.task.lock-at-most:60000}");
    assertThat(lock.lockAtLeastFor()).isEqualTo("${codehunters.outbox.task.lock-at-least:10000}");
  }

  @Test
  void should_read_purge_schedule_from_codehunters_prefix_when_scheduler_declared()
      throws NoSuchMethodException {
    Method method = OutboxPurgeScheduler.class.getMethod("purgePublished");
    Scheduled scheduled = method.getAnnotation(Scheduled.class);
    SchedulerLock lock = method.getAnnotation(SchedulerLock.class);

    assertThat(scheduled.fixedDelayString())
        .isEqualTo("${codehunters.outbox.purge.fixed-delay:3600000}");
    assertThat(scheduled.initialDelayString())
        .isEqualTo("${codehunters.outbox.purge.initial-delay:60000}");
    assertThat(lock.name())
        .isEqualTo(
            "${codehunters.outbox.purge.lock-name:outbox-purge-${spring.application.name:unknown}}");
    assertThat(lock.lockAtMostFor()).isEqualTo("${codehunters.outbox.purge.lock-at-most:PT10M}");
    assertThat(lock.lockAtLeastFor()).isEqualTo("${codehunters.outbox.purge.lock-at-least:PT30S}");
  }
}
