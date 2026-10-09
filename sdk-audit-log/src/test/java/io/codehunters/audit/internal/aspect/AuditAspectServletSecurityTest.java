package io.codehunters.audit.internal.aspect;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.audit.Auditable;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * In a servlet application the authenticated user lives in the calling thread's {@link
 * SecurityContextHolder}; it is the actor when neither the annotation nor the provider names one,
 * and only while a request is bound to the thread: a pooled thread can inherit or leak a context.
 */
class AuditAspectServletSecurityTest {

  private static final Clock FIXED = Clock.fixed(TestConfig.NOW, ZoneOffset.UTC);
  private static final Authentication ALICE =
      new TestingAuthenticationToken("alice", "n/a", "ROLE_USER");
  private static final Authentication ANONYMOUS =
      new AnonymousAuthenticationToken(
          "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

  private final RoutingRecorder recorder = new RoutingRecorder();
  private final AccountService accounts = proxied();
  private final SecurityContextHolderStrategy strategy =
      SecurityContextHolder.getContextHolderStrategy();

  @BeforeEach
  void bindRequest() {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
  }

  @AfterEach
  void clearThreadState() {
    RequestContextHolder.resetRequestAttributes();
    SecurityContextHolder.setContextHolderStrategy(strategy);
    SecurityContextHolder.clearContext();
    ThreadBoundContextProvider.clear();
  }

  @Test
  void should_take_the_actor_from_the_calling_threads_security_context() {
    SecurityContextHolder.getContext().setAuthentication(ALICE);

    accounts.close("ACC-1");

    assertThat(recorder.attached().getFirst().actorId()).isEqualTo("alice");
  }

  @Test
  void should_take_the_actor_on_the_calling_thread_when_the_work_completes_elsewhere() {
    SecurityContextHolder.getContext().setAuthentication(ALICE);
    CompletableFuture<String> pending = new CompletableFuture<>();
    AccountService later = proxied(pending);
    later.closeLater("ACC-1");
    SecurityContextHolder.clearContext();

    pending.complete("closed");

    assertThat(recorder.detached().getFirst().actorId()).isEqualTo("alice");
  }

  @Test
  void should_prefer_the_providers_actor_over_the_security_context() {
    SecurityContextHolder.getContext().setAuthentication(ALICE);
    ThreadBoundContextProvider.actAs("carol");

    accounts.close("ACC-1");

    assertThat(recorder.attached().getFirst().actorId()).isEqualTo("carol");
  }

  @Test
  void should_prefer_the_annotations_actor_over_the_security_context() {
    SecurityContextHolder.getContext().setAuthentication(ALICE);

    accounts.transfer("ACC-1", "bob");

    assertThat(recorder.attached().getFirst().actorId()).isEqualTo("bob");
  }

  @Test
  void should_record_no_actor_when_the_user_is_anonymous() {
    SecurityContextHolder.getContext().setAuthentication(ANONYMOUS);

    accounts.close("ACC-1");

    assertThat(recorder.attached().getFirst().actorId()).isNull();
  }

  @Test
  void should_record_no_actor_when_the_authentication_is_not_authenticated() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("dave", "n/a"));

    accounts.close("ACC-1");

    assertThat(recorder.attached().getFirst().actorId()).isNull();
  }

  @Test
  void should_record_no_actor_when_the_thread_has_no_authentication() {
    accounts.close("ACC-1");

    assertThat(recorder.attached().getFirst().actorId()).isNull();
  }

  @Test
  void should_record_no_actor_when_no_request_is_bound_to_the_thread() {
    RequestContextHolder.resetRequestAttributes();
    SecurityContextHolder.getContext().setAuthentication(ALICE);

    accounts.close("ACC-1");

    assertThat(recorder.attached().getFirst().actorId()).isNull();
  }

  @Test
  void should_still_record_the_event_when_the_security_context_cannot_be_read() {
    SecurityContextHolder.setContextHolderStrategy(new BrokenStrategy());

    accounts.close("ACC-1");

    assertThat(recorder.attached().getFirst().actorId()).isNull();
  }

  private AccountService proxied() {
    return proxied(CompletableFuture.completedFuture("closed"));
  }

  private AccountService proxied(CompletableFuture<String> pending) {
    var factory = new AspectJProxyFactory(new AccountService(pending));
    factory.setProxyTargetClass(true);
    factory.addAspect(
        new AuditAspect(
            recorder, new ThreadBoundContextProvider(), new AuditAspect.Options(FIXED, false, 0)));
    return factory.getProxy();
  }

  /** A security context store that fails on every read. */
  private static final class BrokenStrategy implements SecurityContextHolderStrategy {

    @Override
    public void clearContext() {}

    @Override
    public SecurityContext getContext() {
      throw new IllegalStateException("context unavailable");
    }

    @Override
    public void setContext(SecurityContext context) {}

    @Override
    public SecurityContext createEmptyContext() {
      throw new IllegalStateException("context unavailable");
    }
  }

  /** Audited calls with and without an actor expression. */
  public static class AccountService {

    private final CompletableFuture<String> pending;

    public AccountService(CompletableFuture<String> pending) {
      this.pending = pending;
    }

    @Auditable(entityType = "ACCOUNT", action = "CLOSE", entityId = "#id")
    public void close(String id) {
      // audited only
    }

    @Auditable(entityType = "ACCOUNT", action = "CLOSE", entityId = "#id")
    public CompletionStage<String> closeLater(String id) {
      return pending;
    }

    @Auditable(entityType = "ACCOUNT", action = "TRANSFER", entityId = "#id", actorId = "#to")
    public void transfer(String id, String to) {
      // audited only
    }
  }
}
