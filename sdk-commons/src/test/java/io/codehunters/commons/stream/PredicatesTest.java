package io.codehunters.commons.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PredicatesTest {

  private record Item(String name, String group) {}

  @Test
  void should_keep_first_element_of_each_key() {
    var distinct =
        Stream.of("apple", "avocado", "banana", "blueberry", "cherry")
            .filter(Predicates.distinctByKey(fruit -> fruit.charAt(0)))
            .toList();

    assertThat(distinct).containsExactly("apple", "banana", "cherry");
  }

  @Test
  void should_treat_null_keys_as_one_key() {
    var first = new Item("first", null);
    var second = new Item("second", null);

    var distinct = Stream.of(first, second).filter(Predicates.distinctByKey(Item::group)).toList();

    assertThat(distinct).containsExactly(first);
  }

  @Test
  void should_reject_null_key_extractor() {
    assertThatIllegalArgumentException().isThrownBy(() -> Predicates.distinctByKey(null));
  }
}
