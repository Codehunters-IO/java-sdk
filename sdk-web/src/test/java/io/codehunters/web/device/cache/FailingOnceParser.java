package io.codehunters.web.device.cache;

import io.codehunters.web.device.useragent.UserAgentInfo;
import io.codehunters.web.device.useragent.UserAgentParser;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test fake: throws the given {@link Error} or {@link RuntimeException} on its first call, then
 * echoes the header.
 */
final class FailingOnceParser implements UserAgentParser {

  private final AtomicBoolean failed = new AtomicBoolean();
  private final Throwable failure;

  FailingOnceParser(Error failure) {
    this.failure = failure;
  }

  FailingOnceParser(RuntimeException failure) {
    this.failure = failure;
  }

  @Override
  public UserAgentInfo parse(String userAgent) {
    if (failed.compareAndSet(false, true)) {
      if (failure instanceof Error error) {
        throw error;
      }
      throw (RuntimeException) failure;
    }
    return new UserAgentInfo(userAgent, "1", "Other", "", "Other");
  }
}
