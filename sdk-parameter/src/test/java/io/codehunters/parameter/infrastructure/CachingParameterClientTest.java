package io.codehunters.parameter.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.codehunters.parameter.application.ParameterClient;
import io.codehunters.parameter.domain.exception.ParameterAccessException;
import io.codehunters.parameter.domain.model.ParameterModule;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CachingParameterClientTest {

  private static final Duration TTL = Duration.ofMinutes(5);
  private static final ParameterModule MODULE = ParameterModule.RAFFLES;

  private final ParameterClient delegate = mock(ParameterClient.class);
  private final AtomicLong now = new AtomicLong();
  private final CachingParameterClient cache =
      new CachingParameterClient(delegate, TTL, now::get, 16);

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
  void should_not_cache_a_failure() {
    when(delegate.lookup(MODULE, "T", "MAX"))
        .thenThrow(new ParameterAccessException("down", null))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> cache.lookup(MODULE, "T", "MAX"))
        .isInstanceOf(ParameterAccessException.class);
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
  void should_still_answer_without_caching_when_full() {
    var tiny = new CachingParameterClient(delegate, TTL, now::get, 1);
    when(delegate.lookup(MODULE, "T", "A")).thenReturn(Optional.empty());
    when(delegate.lookup(MODULE, "T", "B")).thenReturn(Optional.empty());

    tiny.lookup(MODULE, "T", "A");
    tiny.lookup(MODULE, "T", "B");
    tiny.lookup(MODULE, "T", "B");

    verify(delegate, times(2)).lookup(MODULE, "T", "B");
  }

  @Test
  void should_make_room_by_dropping_expired_entries_when_full() {
    var tiny = new CachingParameterClient(delegate, TTL, now::get, 1);
    when(delegate.lookup(MODULE, "T", "A")).thenReturn(Optional.empty());
    when(delegate.lookup(MODULE, "T", "B")).thenReturn(Optional.empty());

    tiny.lookup(MODULE, "T", "A");
    now.addAndGet(TTL.toNanos());
    tiny.lookup(MODULE, "T", "B");
    tiny.lookup(MODULE, "T", "B");

    verify(delegate, times(1)).lookup(MODULE, "T", "B");
  }

  @Test
  void should_cache_find_by_module() {
    when(delegate.findByModule(MODULE, null)).thenReturn(List.of());

    cache.findByModule(MODULE, null);
    cache.findByModule(MODULE, null);

    verify(delegate, times(1)).findByModule(MODULE, null);
  }

  @Test
  void should_cache_find_by_modules() {
    when(delegate.findByModules(List.of(MODULE))).thenReturn(List.of());

    cache.findByModules(List.of(MODULE));
    cache.findByModules(List.of(MODULE));

    verify(delegate, times(1)).findByModules(List.of(MODULE));
  }

  @Test
  void should_cache_find_by_module_and_submodules() {
    when(delegate.findByModuleAndSubmodules(MODULE, List.of("T"))).thenReturn(List.of());

    cache.findByModuleAndSubmodules(MODULE, List.of("T"));
    cache.findByModuleAndSubmodules(MODULE, List.of("T"));

    verify(delegate, times(1)).findByModuleAndSubmodules(MODULE, List.of("T"));
  }

  @Test
  void should_cache_as_map() {
    when(delegate.asMap(MODULE, "T")).thenReturn(Map.of());

    cache.asMap(MODULE, "T");
    cache.asMap(MODULE, "T");

    verify(delegate, times(1)).asMap(MODULE, "T");
  }

  @Test
  void should_cache_as_map_by_name() {
    when(delegate.asMapByName(MODULE, "T")).thenReturn(Map.of());

    cache.asMapByName(MODULE, "T");
    cache.asMapByName(MODULE, "T");

    verify(delegate, times(1)).asMapByName(MODULE, "T");
  }

  @Test
  void should_reject_a_ttl_that_is_not_positive() {
    assertThatThrownBy(() -> new CachingParameterClient(delegate, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
