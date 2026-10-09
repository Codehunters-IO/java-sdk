package io.codehunters.audit.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.audit.internal.reactive.ReactorHidingClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Without Spring Security on the classpath, the calling thread simply has no actor. */
class CallingThreadActorWithoutSecurityTest {

  private final ReactorHidingClassLoader loader =
      new ReactorHidingClassLoader("org.springframework.security.");

  @AfterEach
  void closeLoader() throws Exception {
    loader.close();
  }

  @Test
  void should_report_no_actor_without_spring_security() throws Exception {
    Object actor =
        loader
            .loadClass("io.codehunters.audit.internal.security.CallingThreadActor")
            .getMethod("current")
            .invoke(null);

    assertThat(actor).isNull();
  }
}
