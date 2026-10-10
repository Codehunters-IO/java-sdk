package io.codehunters.messaging.jdbc.config;

import io.codehunters.messaging.OutboxDispatchStore;
import io.codehunters.messaging.OutboxDispatchTrigger;
import io.codehunters.messaging.SendOutboxMessagesPort;
import io.codehunters.messaging.config.EventMessagingCoreAutoConfiguration;
import io.codehunters.messaging.config.EventMessagingProperties;
import io.codehunters.messaging.dispatch.CoalescingDispatchTrigger;
import io.codehunters.messaging.jdbc.scheduler.OutboxPurgeScheduler;
import io.codehunters.messaging.jdbc.scheduler.OutboxPurgeSettings;
import io.codehunters.messaging.jdbc.scheduler.OutboxScheduledTask;
import io.codehunters.messaging.jdbc.scheduler.OutboxScheduler;
import io.codehunters.messaging.jdbc.scheduler.OutboxSchedulingLifecycle;
import io.codehunters.messaging.jdbc.scheduler.OutboxTaskLock;
import io.codehunters.messaging.jdbc.scheduler.OutboxTaskSchedule;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Spring Boot auto-configuration for the ShedLock-protected outbox dispatch and purge tasks. Active
 * when {@code codehunters.outbox.enabled} and {@code codehunters.outbox.scheduling.enabled} are
 * {@code true} (both default) and an {@link OutboxDispatchStore} and a ShedLock {@link
 * LockProvider} exist; with scheduling off, the application triggers {@link
 * SendOutboxMessagesPort#execute()} and the purge itself.
 *
 * <p>The tasks run on the SDK's own {@code outboxTaskScheduler}, registered programmatically by
 * {@link OutboxSchedulingLifecycle}: application-wide scheduling ({@code @EnableScheduling}) is not
 * turned on, and the scheduler is not a default candidate, so the application's {@link
 * TaskScheduler} is neither replaced by it nor shared with it.
 *
 * <p>Separate from {@link EventMessagingJdbcAutoConfiguration} because it must run after {@link
 * EventMessagingCoreAutoConfiguration}, which creates the {@link SendOutboxMessagesPort} the
 * dispatch task triggers, while the JDBC configuration must run before it.
 */
@AutoConfiguration(after = EventMessagingCoreAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "codehunters.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnProperty(
    prefix = "codehunters.outbox.scheduling",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnBean({OutboxDispatchStore.class, LockProvider.class})
public class OutboxDispatchSchedulerAutoConfiguration {

  private static final String OUTBOX_TASK_SCHEDULER = "outboxTaskScheduler";
  private static final String DISPATCH_TRIGGER = "outboxDispatchTrigger";
  private static final int POOL_SIZE = 3;

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public OutboxDispatchSchedulerAutoConfiguration() {}

  /**
   * Provides the SDK's own task scheduler: three threads named {@code outbox-}, one each for the
   * dispatch poll, the purge and the commit-time dispatch. It is not a default candidate, so
   * injecting a {@link TaskScheduler} by type never sees it. A replacement bean of the same name
   * must also be an {@link Executor} while {@code scheduling.dispatch-on-commit} is on.
   *
   * <p>On shutdown it waits for a running dispatch to end, up to {@link
   * EventMessagingProperties#maxDispatchRun()} (half the claim TTL plus one transport timeout), so
   * a batch is published and written back rather than cut off mid-send and reclaimed later.
   *
   * @param properties the messaging configuration
   * @return a {@link ThreadPoolTaskScheduler}
   */
  @Bean(name = OUTBOX_TASK_SCHEDULER, defaultCandidate = false)
  @ConditionalOnMissingBean(name = OUTBOX_TASK_SCHEDULER)
  public ThreadPoolTaskScheduler outboxTaskScheduler(EventMessagingProperties properties) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(POOL_SIZE);
    scheduler.setThreadNamePrefix("outbox-");
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationMillis(properties.maxDispatchRun().toMillis());
    return scheduler;
  }

  /**
   * Provides the trigger that dispatches right after a transaction that saved outbox messages
   * commits, and keeps dispatching while claims come back full. It runs on the {@code
   * outboxTaskScheduler}, one run at a time, without the dispatch lock: concurrent claims are safe
   * because the claim skips locked rows, fences its updates and keeps each key in order. The
   * dispatch port is resolved on each run, so the outbox store can hold this trigger although the
   * dispatch use case depends on the store. Off with {@code
   * codehunters.outbox.scheduling.dispatch-on-commit=false}.
   *
   * @param executor the SDK's {@code outboxTaskScheduler}
   * @param sendOutboxMessagesPort the dispatch port, looked up lazily
   * @return a {@link CoalescingDispatchTrigger}
   */
  @Bean(name = DISPATCH_TRIGGER)
  @ConditionalOnMissingBean(name = DISPATCH_TRIGGER)
  @ConditionalOnBean(SendOutboxMessagesPort.class)
  @ConditionalOnProperty(
      prefix = "codehunters.outbox.scheduling",
      name = "dispatch-on-commit",
      havingValue = "true",
      matchIfMissing = true)
  public OutboxDispatchTrigger outboxDispatchTrigger(
      @Qualifier(OUTBOX_TASK_SCHEDULER) Executor executor,
      ObjectProvider<SendOutboxMessagesPort> sendOutboxMessagesPort) {
    return new CoalescingDispatchTrigger(executor, sendOutboxMessagesPort::getIfAvailable);
  }

  /**
   * Provides the task that periodically triggers outbox dispatch. A poll whose claim comes back
   * full hands the rest of the backlog to the {@code outboxDispatchTrigger}, when there is one.
   *
   * @param sendOutboxMessagesPort the dispatch port
   * @param dispatchTrigger the commit-time dispatch trigger, if any
   * @return an {@link OutboxScheduler}
   */
  @Bean
  @ConditionalOnMissingBean(OutboxScheduler.class)
  @ConditionalOnBean(SendOutboxMessagesPort.class)
  public OutboxScheduler outboxScheduler(
      SendOutboxMessagesPort sendOutboxMessagesPort,
      ObjectProvider<OutboxDispatchTrigger> dispatchTrigger) {
    return new OutboxScheduler(
        sendOutboxMessagesPort, dispatchTrigger.getIfAvailable(OutboxDispatchTrigger::none));
  }

  /**
   * Provides the task that deletes PUBLISHED rows older than {@code
   * codehunters.outbox.purge.retention}.
   *
   * @param outboxDispatchStore the outbox store
   * @param properties the messaging configuration
   * @param clock the SDK's private {@code outboxClock}
   * @return an {@link OutboxPurgeScheduler}
   */
  @Bean
  @ConditionalOnMissingBean(OutboxPurgeScheduler.class)
  public OutboxPurgeScheduler outboxPurgeScheduler(
      OutboxDispatchStore outboxDispatchStore,
      EventMessagingProperties properties,
      @Qualifier("outboxClock") Clock clock) {
    EventMessagingProperties.Purge purge = properties.purge();
    return new OutboxPurgeScheduler(
        outboxDispatchStore, new OutboxPurgeSettings(purge.retention(), purge.batchSize(), clock));
  }

  /**
   * Schedules the dispatch task from the start, every {@code task.fixed-delay}, under the {@code
   * task.*} lock.
   *
   * @param outboxScheduler the dispatch task
   * @param properties the messaging configuration
   * @param applicationName the application name, which names the lock when {@code task.lock-name}
   *     is unset
   * @return the scheduled dispatch task
   */
  @Bean
  @ConditionalOnMissingBean(name = "outboxDispatchTask")
  @ConditionalOnBean(SendOutboxMessagesPort.class)
  public OutboxScheduledTask outboxDispatchTask(
      OutboxScheduler outboxScheduler,
      EventMessagingProperties properties,
      @Value("${spring.application.name:unknown}") String applicationName) {
    EventMessagingProperties.Task task = properties.task();
    String lockName = Objects.requireNonNullElse(task.lockName(), "outbox-" + applicationName);
    return new OutboxScheduledTask(
        outboxScheduler,
        new OutboxTaskSchedule(Duration.ZERO, task.fixedDelay()),
        new OutboxTaskLock(lockName, task.lockAtMost(), task.lockAtLeast()));
  }

  /**
   * Schedules the purge task after {@code purge.initial-delay}, every {@code purge.fixed-delay},
   * under the {@code purge.*} lock.
   *
   * @param outboxPurgeScheduler the purge task
   * @param properties the messaging configuration
   * @param applicationName the application name, which names the lock when {@code purge.lock-name}
   *     is unset
   * @return the scheduled purge task
   */
  @Bean
  @ConditionalOnMissingBean(name = "outboxPurgeTask")
  public OutboxScheduledTask outboxPurgeTask(
      OutboxPurgeScheduler outboxPurgeScheduler,
      EventMessagingProperties properties,
      @Value("${spring.application.name:unknown}") String applicationName) {
    EventMessagingProperties.Purge purge = properties.purge();
    String lockName =
        Objects.requireNonNullElse(purge.lockName(), "outbox-purge-" + applicationName);
    return new OutboxScheduledTask(
        outboxPurgeScheduler,
        new OutboxTaskSchedule(purge.initialDelay(), purge.fixedDelay()),
        new OutboxTaskLock(lockName, purge.lockAtMost(), purge.lockAtLeast()));
  }

  /**
   * Registers every {@link OutboxScheduledTask} bean on the outbox scheduler when the context
   * starts, and stops the {@code outboxDispatchTrigger}, when it is the SDK's {@link
   * CoalescingDispatchTrigger}, when the context stops: no new commit-time drain starts, and one in
   * progress ends after its current run.
   *
   * @param scheduler the SDK's {@code outboxTaskScheduler}
   * @param lockProvider the ShedLock provider
   * @param tasks the scheduled tasks
   * @param dispatchTrigger the commit-time dispatch trigger, if any
   * @return an {@link OutboxSchedulingLifecycle}
   */
  @Bean
  @ConditionalOnMissingBean(OutboxSchedulingLifecycle.class)
  public OutboxSchedulingLifecycle outboxSchedulingLifecycle(
      @Qualifier(OUTBOX_TASK_SCHEDULER) TaskScheduler scheduler,
      LockProvider lockProvider,
      ObjectProvider<OutboxScheduledTask> tasks,
      @Qualifier(DISPATCH_TRIGGER) ObjectProvider<OutboxDispatchTrigger> dispatchTrigger) {
    var lifecycle =
        new OutboxSchedulingLifecycle(scheduler, lockProvider, tasks.orderedStream().toList());
    return dispatchTrigger.getIfAvailable() instanceof CoalescingDispatchTrigger coalescing
        ? lifecycle.onStop(coalescing::stop)
        : lifecycle;
  }
}
