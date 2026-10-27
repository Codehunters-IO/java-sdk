package io.codehunters.messaging.kafka.consumer;

import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.W3cTraceParent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer.HeaderNames;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer.SingleRecordHeader;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.TimestampedException;
import org.springframework.kafka.retrytopic.DeadLetterPublishingRecovererFactory;
import org.springframework.kafka.retrytopic.DestinationTopicResolver;
import org.springframework.kafka.retrytopic.RetryTopicComponentFactory;
import org.springframework.kafka.support.KafkaHeaders;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Retry-topic components that keep exception messages and stack traces off every forwarded record
 * and out of the logs, and run the {@link DeadLetterHook}s before a record reaches its dead-letter
 * topic. Applies to every retry-topic configuration of the application.
 *
 * <p>On every hop (main topic to {@code -retry-0}, ..., last retry to {@code .DLT}) the record
 * value and headers are forwarded unchanged. The exception headers name the listener's own
 * exception class and its root cause class only, and an {@code event-id} and a {@code trace-id}
 * header are added. Spring's original topic, partition, offset and attempt headers are kept. The
 * listener exception is never logged; one WARN per dead-lettered record names its topic, partition,
 * offset and failure class.
 */
public class SanitizedDeadLetters extends RetryTopicComponentFactory {

  private static final Logger log = LoggerFactory.getLogger(SanitizedDeadLetters.class);
  private static final int MAX_UNWRAP = 10;
  private static final JsonMapper ENVELOPE_PEEK = JsonMapper.builder().build();
  private static final List<String> FREE_TEXT_HEADERS =
      List.of(
          KafkaHeaders.DLT_EXCEPTION_MESSAGE,
          KafkaHeaders.DLT_EXCEPTION_STACKTRACE,
          KafkaHeaders.DLT_KEY_EXCEPTION_MESSAGE,
          KafkaHeaders.DLT_KEY_EXCEPTION_STACKTRACE);

  private final List<DeadLetterHook> hooks;

  /**
   * Creates the components.
   *
   * @param hooks the hooks to call before a record is dead-lettered, in order
   */
  public SanitizedDeadLetters(List<DeadLetterHook> hooks) {
    this.hooks = List.copyOf(hooks);
  }

  /**
   * Configures Spring's recoverer factory: no listener-exception logging, class-name-only exception
   * headers, correlation headers, and the hooks before each dead-letter publish.
   *
   * @param destinationTopicResolver the resolver of the next hop
   * @return the configured factory
   */
  @Override
  public DeadLetterPublishingRecovererFactory deadLetterPublishingRecovererFactory(
      DestinationTopicResolver destinationTopicResolver) {
    DeadLetterPublishingRecovererFactory factory =
        super.deadLetterPublishingRecovererFactory(destinationTopicResolver);
    factory.neverLogListenerException();
    factory.setDeadLetterPublisherCreator(
        (templates, destinations) ->
            new DeadLetterPublishingRecoverer(templates, hooking(destinations)));
    factory.setDeadLetterPublishingRecovererCustomizer(SanitizedDeadLetters::sanitize);
    factory.setHeadersFunction(SanitizedDeadLetters::correlationHeaders);
    return factory;
  }

  private static void sanitize(DeadLetterPublishingRecoverer recoverer) {
    recoverer.setExceptionHeadersCreator(SanitizedDeadLetters::classNamesOnly);
    recoverer.setLogRecoveryRecord(false);
  }

  // The resolver runs once per recovery, so the hooks run once per dead-lettered record.
  BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> hooking(
      BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> destinations) {
    return (record, exception) -> {
      TopicPartition target = destinations.apply(record, exception);
      if (target != null && target.topic().endsWith(InboundRetryTopics.DLT_SUFFIX)) {
        beforeDeadLetter(new DeadLetter(record, listenerFailure(exception)));
      }
      return target;
    };
  }

  private void beforeDeadLetter(DeadLetter deadLetter) {
    ConsumerRecord<?, ?> record = deadLetter.record();
    log.warn(
        "Dead-lettering record {}-{}@{} after {}",
        record.topic(),
        record.partition(),
        record.offset(),
        deadLetter.failure().getClass().getName());
    hooks.forEach(hook -> hook.beforeDeadLetter(deadLetter));
  }

  static void classNamesOnly(
      Headers headers, Exception exception, boolean isKey, HeaderNames names) {
    HeaderNames.ExceptionInfo info = names.getExceptionInfo();
    Throwable failure = listenerFailure(exception);
    stripExceptionHeaders(headers, info);
    String fqcnHeader = isKey ? info.getKeyExceptionFqcn() : info.getExceptionFqcn();
    headers.add(fqcnHeader, bytes(failure.getClass().getName()));
    rootCause(failure)
        .ifPresent(
            cause -> headers.add(info.getExceptionCauseFqcn(), bytes(cause.getClass().getName())));
  }

  private static void stripExceptionHeaders(Headers headers, HeaderNames.ExceptionInfo info) {
    List.of(
            info.getExceptionFqcn(),
            info.getKeyExceptionFqcn(),
            info.getExceptionCauseFqcn(),
            info.getExceptionMessage(),
            info.getKeyExceptionMessage(),
            info.getExceptionStacktrace(),
            info.getKeyExceptionStacktrace())
        .forEach(headers::remove);
    FREE_TEXT_HEADERS.forEach(headers::remove);
  }

  static Headers correlationHeaders(ConsumerRecord<?, ?> record, Exception exception) {
    Headers headers = new RecordHeaders();
    eventId(record)
        .ifPresent(
            id -> headers.add(new SingleRecordHeader(DeadLetterHook.EVENT_ID_HEADER, bytes(id))));
    W3cTraceParent.parse(text(record, MessagePublisherPort.TRACEPARENT_HEADER))
        .ifPresent(
            traceparent ->
                headers.add(
                    new SingleRecordHeader(
                        DeadLetterHook.TRACE_ID_HEADER, bytes(traceparent.traceId()))));
    return headers;
  }

  private static Optional<String> eventId(ConsumerRecord<?, ?> record) {
    return envelopeEventId(record.value())
        .or(() -> Optional.ofNullable(text(record, MessagePublisherPort.OUTBOX_ID_HEADER)));
  }

  // Best effort: a value that is not an envelope only loses this header, never the recovery.
  private static Optional<String> envelopeEventId(Object value) {
    if (!(value instanceof String json)) {
      return Optional.empty();
    }
    try {
      JsonNode eventId = ENVELOPE_PEEK.readTree(json).path("header").path("eventId");
      return eventId.isString() ? Optional.of(eventId.stringValue()) : Optional.empty();
    } catch (JacksonException e) {
      return Optional.empty();
    }
  }

  static Throwable listenerFailure(Throwable exception) {
    Throwable current = exception;
    for (int depth = 0; depth < MAX_UNWRAP && isWrapper(current); depth++) {
      current = current.getCause();
    }
    return current;
  }

  private static boolean isWrapper(Throwable exception) {
    return (exception instanceof ListenerExecutionFailedException
            || exception instanceof TimestampedException)
        && exception.getCause() != null;
  }

  private static Optional<Throwable> rootCause(Throwable failure) {
    Throwable cause = failure.getCause();
    for (int depth = 0; depth < MAX_UNWRAP && cause != null && cause.getCause() != null; depth++) {
      cause = cause.getCause();
    }
    return Optional.ofNullable(cause);
  }

  private static String text(ConsumerRecord<?, ?> record, String name) {
    Header header = record.headers().lastHeader(name);
    return header == null || header.value() == null
        ? null
        : new String(header.value(), StandardCharsets.UTF_8);
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
