package io.codehunters.security.authz.servlet;

import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.keycloak.adapters.authorization.PolicyEnforcer;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig;

/**
 * The filter with Keycloak's enforcer construction replaced by a script: every build either fails
 * as an unreachable Keycloak would, or hands out a given enforcer. A build can be held until the
 * test releases it.
 */
final class ScriptedEnforcerFilter extends KeycloakPolicyEnforcerFilter {

  private final AtomicInteger attempts = new AtomicInteger();
  private volatile Supplier<PolicyEnforcer> builds = ScriptedEnforcerFilter::unreachable;
  private volatile CountDownLatch entered = new CountDownLatch(0);
  private volatile CountDownLatch release = new CountDownLatch(0);

  ScriptedEnforcerFilter(Options options) {
    super(new PolicyEnforcerConfig(), null, options);
  }

  void failBuilds() {
    builds = ScriptedEnforcerFilter::unreachable;
  }

  void buildWith(PolicyEnforcer enforcer) {
    builds = () -> enforcer;
  }

  void holdNextBuild() {
    entered = new CountDownLatch(1);
    release = new CountDownLatch(1);
  }

  void awaitHeldBuild() throws InterruptedException {
    entered.await(5, TimeUnit.SECONDS);
  }

  void releaseBuild() {
    release.countDown();
  }

  int attempts() {
    return attempts.get();
  }

  @Override
  protected PolicyEnforcer createPolicyEnforcer(
      HttpServletRequest request, PolicyEnforcerConfig config) {
    attempts.incrementAndGet();
    entered.countDown();
    awaitRelease();
    return builds.get();
  }

  private void awaitRelease() {
    try {
      release.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static PolicyEnforcer unreachable() {
    throw new IllegalStateException("Keycloak unreachable");
  }
}
