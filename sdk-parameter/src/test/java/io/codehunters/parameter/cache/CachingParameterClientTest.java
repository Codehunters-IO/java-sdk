package io.codehunters.parameter.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.codehunters.parameter.Parameter;
import io.codehunters.parameter.ParameterAccessException;
import io.codehunters.parameter.ParameterClient;
import io.codehunters.parameter.ParameterModule;
import io.codehunters.parameter.ParameterType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CachingParameterClientTest {

  private static final Duration TTL = Duration.ofMinutes(5);
  private static final ParameterModule MODULE = ParameterModule.of("ORDERS");
  private static final ParameterModule AUTH = ParameterModule.of("AUTH");
  private static final Parameter MAX = parameter("MAX", "100");
  private static final Parameter MIN = parameter("MIN", "1");

  private final ParameterClient delegate = mock(ParameterClient.class);
  private final AtomicLong now = new AtomicLong();
  private final ParameterCache entries = ParameterCache.create(TTL, 16, now::get);
  private final CachingParameterClient cache = new CachingParameterClient(delegate, entries);

  @Test
  void should_serve_a_lookup_from_cache_within_the_ttl() {
    when(delegate.lookup(MODULE, "T", "MAX")).thenReturn(Optional.empty());

    cache.lookup(MODULE, "T", "MAX");
    cache.lookup(MODULE, "T", "MAX");

    verify(delegate, times(1)).lookup(MODULE, "T", "MAX");
  }

  @Test
  void should_reload_once_the_ttl_has_elapsed() {
    when(delegate.lookup(MODULE, "T", "MAX")).thenReturn(Optional.empty());

    cache.lookup(MODULE, "T", "MAX");
    now.addAndGet(TTL.toNanos());
    cache.lookup(MODULE, "T", "MAX");

    verify(delegate, times(2)).lookup(MODULE, "T", "MAX");
  }

  @Test
  void should_serve_from_cache_until_just_before_the_ttl() {
    when(delegate.lookup(MODULE, "T", "MAX")).thenReturn(Optional.empty());

    cache.lookup(MODULE, "T", "MAX");
    now.addAndGet(TTL.toNanos() - 1);
    cache.lookup(MODULE, "T", "MAX");

    verify(delegate, times(1)).lookup(MODULE, "T", "MAX");
  }

  @Test
  void should_not_cache_a_failure() {
    when(delegate.lookup(MODULE, "T", "MAX"))
        .thenThrow(new ParameterAccessException("down", null))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> cache.lookup(MODULE, "T", "MAX"))
        .isInstanceOf(ParameterAccessException.class);
    assertThat(cache.lookup(MODULE, "T", "MAX")).isEmpty();
  }

  @Test
  void should_rethrow_an_error_from_the_delegate_without_caching_it() {
    when(delegate.lookup(MODULE, "T", "MAX"))
        .thenThrow(new StackOverflowError())
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> cache.lookup(MODULE, "T", "MAX"))
        .isInstanceOf(StackOverflowError.class);
    assertThat(cache.lookup(MODULE, "T", "MAX")).isEmpty();
  }

  @Test
  void should_cache_each_set_of_arguments_separately() {
    when(delegate.lookup(MODULE, "T", "MAX")).thenReturn(Optional.empty());
    when(delegate.lookup(MODULE, "T", "MIN")).thenReturn(Optional.empty());

    cache.lookup(MODULE, "T", "MAX");
    cache.lookup(MODULE, "T", "MIN");

    verify(delegate, times(1)).lookup(MODULE, "T", "MIN");
  }

  @Test
  void should_keep_at_most_the_configured_number_of_answers() {
    var small = ParameterCache.create(TTL, 2, now::get);
    var client = new CachingParameterClient(delegate, small);
    when(delegate.lookup(eq(MODULE), eq("T"), any())).thenReturn(Optional.empty());

    client.lookup(MODULE, "T", "A");
    client.lookup(MODULE, "T", "B");
    client.lookup(MODULE, "T", "C");

    assertThat(small.size()).isEqualTo(2);
  }

  @Test
  void should_load_again_after_the_module_is_invalidated() {
    when(delegate.lookup(MODULE, "T", "MAX")).thenReturn(Optional.empty());

    cache.lookup(MODULE, "T", "MAX");
    entries.invalidate(MODULE);
    cache.lookup(MODULE, "T", "MAX");

    verify(delegate, times(2)).lookup(MODULE, "T", "MAX");
  }

  @Test
  void should_keep_other_modules_when_one_module_is_invalidated() {
    when(delegate.lookup(any(), eq("T"), eq("MAX"))).thenReturn(Optional.empty());

    cache.lookup(AUTH, "T", "MAX");
    entries.invalidate(MODULE);
    cache.lookup(AUTH, "T", "MAX");

    verify(delegate, times(1)).lookup(AUTH, "T", "MAX");
  }

  @Test
  void should_drop_every_query_naming_the_invalidated_module() {
    when(delegate.findByModules(any())).thenReturn(List.of());

    cache.findByModules(List.of(AUTH, MODULE));
    entries.invalidate(MODULE);
    cache.findByModules(List.of(AUTH, MODULE));

    verify(delegate, times(2)).findByModules(any());
  }

  @Test
  void should_drop_submodule_queries_when_their_module_is_invalidated() {
    when(delegate.findByModuleAndSubmodules(eq(MODULE), any())).thenReturn(List.of());

    cache.findByModuleAndSubmodules(MODULE, List.of("A"));
    entries.invalidate(MODULE);
    cache.findByModuleAndSubmodules(MODULE, List.of("A"));

    verify(delegate, times(2)).findByModuleAndSubmodules(eq(MODULE), any());
  }

  @Test
  void should_load_everything_again_after_invalidating_all() {
    when(delegate.findByModule(any(), any())).thenReturn(List.of());

    cache.findByModule(MODULE, null);
    cache.findByModule(AUTH, null);
    entries.invalidateAll();
    cache.findByModule(MODULE, null);
    cache.findByModule(AUTH, null);

    verify(delegate, times(2)).findByModule(MODULE, null);
    verify(delegate, times(2)).findByModule(AUTH, null);
  }

  @Test
  void should_share_one_load_between_concurrent_callers() throws InterruptedException {
    var calls = new ConcurrentCalls(delegate);
    calls.blockLookupWith(Optional.of(MAX));

    calls.startLoaderAndWaiter(() -> cache.lookup(MODULE, "T", "MAX"));

    assertThat(calls.results()).containsExactly(Optional.of(MAX), Optional.of(MAX));
    verify(delegate, times(1)).lookup(MODULE, "T", "MAX");
  }

  @Test
  void should_pass_a_failed_load_to_concurrent_callers_without_caching_it()
      throws InterruptedException {
    var calls = new ConcurrentCalls(delegate);
    calls.failLookupWith(new ParameterAccessException("down", null));

    calls.startLoaderAndWaiter(() -> cache.lookup(MODULE, "T", "MAX"));

    assertThat(calls.failures()).hasSize(2).allMatch(ParameterAccessException.class::isInstance);
    assertThat(cache.lookup(MODULE, "T", "MAX")).isEmpty();
  }

  @Test
  void should_treat_a_null_and_a_blank_submodule_as_the_same_lookup() {
    when(delegate.lookup(eq(MODULE), any(), eq("MAX"))).thenReturn(Optional.empty());

    cache.lookup(MODULE, null, "MAX");
    cache.lookup(MODULE, "  ", "MAX");

    verify(delegate, times(1)).lookup(eq(MODULE), any(), eq("MAX"));
  }

  @Test
  void should_keep_all_submodules_apart_from_the_empty_submodule() {
    when(delegate.findByModule(eq(MODULE), any())).thenReturn(List.of());

    cache.findByModule(MODULE, null);
    cache.findByModule(MODULE, "");

    verify(delegate, times(2)).findByModule(eq(MODULE), any());
  }

  @Test
  void should_treat_reordered_and_repeated_submodules_as_the_same_query() {
    when(delegate.findByModuleAndSubmodules(eq(MODULE), any())).thenReturn(List.of());

    cache.findByModuleAndSubmodules(MODULE, List.of("B", "A", "A"));
    cache.findByModuleAndSubmodules(MODULE, List.of("A", "B"));

    verify(delegate, times(1)).findByModuleAndSubmodules(eq(MODULE), any());
  }

  @Test
  void should_treat_reordered_and_repeated_modules_as_the_same_query() {
    when(delegate.findByModules(any())).thenReturn(List.of());

    cache.findByModules(List.of(AUTH, MODULE));
    cache.findByModules(List.of(MODULE, AUTH, MODULE));

    verify(delegate, times(1)).findByModules(any());
  }

  @Test
  void should_not_follow_changes_to_the_caller_collection() {
    when(delegate.findByModuleAndSubmodules(eq(MODULE), any())).thenReturn(List.of());
    var submodules = new ArrayList<>(List.of("A"));

    cache.findByModuleAndSubmodules(MODULE, submodules);
    submodules.add("B");
    cache.findByModuleAndSubmodules(MODULE, List.of("A", "B"));

    verify(delegate, times(2)).findByModuleAndSubmodules(eq(MODULE), any());
  }

  @Test
  void should_not_follow_changes_to_the_list_the_delegate_returned() {
    var answer = new ArrayList<>(List.of(MAX));
    when(delegate.findByModule(MODULE, null)).thenReturn(answer);

    cache.findByModule(MODULE, null);
    answer.clear();

    assertThat(cache.findByModule(MODULE, null)).containsExactly(MAX);
  }

  @Test
  void should_return_an_unmodifiable_cached_list() {
    when(delegate.findByModule(MODULE, null)).thenReturn(new ArrayList<>(List.of(MAX)));

    List<Parameter> result = cache.findByModule(MODULE, null);

    assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void should_cache_find_by_modules() {
    when(delegate.findByModules(List.of(MODULE))).thenReturn(List.of());

    cache.findByModules(List.of(MODULE));
    cache.findByModules(List.of(MODULE));

    verify(delegate, times(1)).findByModules(List.of(MODULE));
  }

  @Test
  void should_build_as_map_from_the_cached_find_by_module() {
    when(delegate.findByModule(MODULE, "T")).thenReturn(List.of(MAX, MIN));

    cache.findByModule(MODULE, "T");

    assertThat(cache.asMap(MODULE, "T")).containsEntry("MAX", "100").containsEntry("MIN", "1");
    verify(delegate, times(1)).findByModule(MODULE, "T");
    verify(delegate, never()).asMap(any(), any());
  }

  @Test
  void should_build_as_map_by_name_from_the_cached_find_by_module() {
    when(delegate.findByModule(MODULE, "T")).thenReturn(List.of(MAX, MIN));

    cache.findByModule(MODULE, "T");

    assertThat(cache.asMapByName(MODULE, "T")).containsEntry("MAX", MAX);
    verify(delegate, times(1)).findByModule(MODULE, "T");
    verify(delegate, never()).asMapByName(any(), any());
  }

  @Test
  void should_reject_a_null_module_without_calling_the_delegate() {
    assertThatThrownBy(() -> cache.findByModules(Arrays.asList(MODULE, null)))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(delegate);
  }

  @Test
  void should_reject_a_null_submodule_without_calling_the_delegate() {
    assertThatThrownBy(() -> cache.findByModuleAndSubmodules(MODULE, Arrays.asList("A", null)))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(delegate);
  }

  @Test
  void should_reject_a_ttl_that_is_not_positive() {
    assertThatThrownBy(() -> new CachingParameterClient(delegate, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_a_disabled_cache() {
    assertThatThrownBy(() -> new CachingParameterClient(delegate, ParameterCache.disabled()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_cache_with_the_default_size_when_built_from_a_ttl() {
    var client = new CachingParameterClient(delegate, TTL);
    when(delegate.lookup(MODULE, "T", "MAX")).thenReturn(Optional.empty());

    client.lookup(MODULE, "T", "MAX");
    client.lookup(MODULE, "T", "MAX");

    verify(delegate, times(1)).lookup(MODULE, "T", "MAX");
  }

  private static Parameter parameter(String name, String value) {
    return new Parameter(
        null, MODULE, "T", name, value, ParameterType.STRING, true, null, null, null, null, null);
  }
}
