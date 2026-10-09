package io.codehunters.messaging.jdbc.config;

import io.codehunters.messaging.OutboxDispatchStore;
import io.codehunters.messaging.SendOutboxMessagesPort;
import io.codehunters.messaging.config.EventMessagingCoreAutoConfiguration;
import io.codehunters.messaging.config.EventMessagingProperties;
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
  private static final int POOL_SIZE = 2;

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public OutboxDispatchSchedulerAutoConfiguration() {}

  /**
   * Provides the SDK's own task scheduler: two threads named {@code outbox-}, one per task. It is
   * not a default candidate, so injecting a {@link TaskScheduler} by type never sees it.
   *
   * @return a {@link ThreadPoolTaskScheduler}
   */
  @Bean(name = OUTBOX_TASK_SCHEDULER, defaultCandidate = false)
  @ConditionalOnMissingBean(name = OUTBOX_TASK_SCHEDULER)
  public ThreadPoolTaskScheduler outboxTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(POOL_SIZE);
    scheduler.setThreadNamePrefix("outbox-");
    return scheduler;
  }

  /**
   * Provides the task that periodically triggers outbox dispatch.
   *
   * @param sendOutboxMessagesPort the dispatch port
   * @return an {@link OutboxScheduler}
   */
  @Bean
  @ConditionalOnMissingBean(OutboxScheduler.class)
  @ConditionalOnBean(SendOutboxMessagesPort.class)
  public OutboxScheduler outboxScheduler(SendOutboxMessagesPort sendOutboxMessagesPort) {
    return new OutboxScheduler(sendOutboxMessagesPort);
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
   * starts.
   *
   * @param scheduler the SDK's {@code outboxTaskScheduler}
   * @param lockProvider the ShedLock provider
   * @param tasks the scheduled tasks
   * @return an {@link OutboxSchedulingLifecycle}
   */
  @Bean
  @ConditionalOnMissingBean(OutboxSchedulingLifecycle.class)
  public OutboxSchedulingLifecycle outboxSchedulingLifecycle(
      @Qualifier(OUTBOX_TASK_SCHEDULER) TaskScheduler scheduler,
      LockProvider lockProvider,
      ObjectProvider<OutboxScheduledTask> tasks) {
    return new OutboxSchedulingLifecycle(scheduler, lockProvider, tasks.orderedStream().toList());
  }
}
