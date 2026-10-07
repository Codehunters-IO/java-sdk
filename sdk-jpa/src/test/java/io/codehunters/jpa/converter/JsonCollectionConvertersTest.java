package io.codehunters.jpa.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JsonCollectionConvertersTest {

  private static final UUID FIRST = UUID.fromString("0192f3a0-0000-7000-8000-000000000001");

  @Test
  void should_keep_list_order_and_null_elements() {
    var converter = new StringListConverter();
    var tags = Arrays.asList("b", null, "a");

    var json = converter.convertToDatabaseColumn(tags);

    assertThat(json).isEqualTo("[\"b\",null,\"a\"]");
    assertThat(converter.convertToEntityAttribute(json)).containsExactly("b", null, "a");
  }

  @Test
  void should_store_an_empty_list_as_an_empty_json_array() {
    assertThat(new StringListConverter().convertToDatabaseColumn(List.of())).isEqualTo("[]");
  }

  @Test
  void should_read_lists_that_callers_cannot_change() {
    var read = new StringListConverter().convertToEntityAttribute("[\"a\"]");

    assertThatThrownBy(() -> read.add("b")).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void should_read_typed_elements_when_given_their_class() {
    var converter = new UuidListConverter();

    assertThat(converter.convertToEntityAttribute("[\"" + FIRST + "\"]")).containsExactly(FIRST);
  }

  @Test
  void should_keep_set_order_and_drop_duplicates() {
    var converter = new StringSetConverter();

    var read = converter.convertToEntityAttribute("[\"b\",\"a\",\"b\"]");

    assertThat(read).containsExactly("b", "a");
    assertThat(converter.convertToDatabaseColumn(new LinkedHashSet<>(List.of("b", "a"))))
        .isEqualTo("[\"b\",\"a\"]");
    assertThatThrownBy(() -> read.add("c")).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void should_keep_map_order_and_null_values() {
    var converter = new StringMapConverter();
    var attributes = new LinkedHashMap<String, String>();
    attributes.put("size", "XL");
    attributes.put("gift", null);

    var json = converter.convertToDatabaseColumn(attributes);
    var read = converter.convertToEntityAttribute(json);

    assertThat(json).isEqualTo("{\"size\":\"XL\",\"gift\":null}");
    assertThat(read).containsExactly(Map.entry("size", "XL"), entry("gift", null));
    assertThatThrownBy(() -> read.put("c", "d")).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void should_report_a_column_that_is_not_the_expected_shape() {
    assertThatThrownBy(() -> new StringListConverter().convertToEntityAttribute("{\"a\":1}"))
        .isInstanceOf(JsonColumnException.class)
        .hasMessageContaining("List");
  }

  @Test
  void should_reject_a_missing_element_type() {
    assertThatThrownBy(() -> new JsonListConverter<String>(null, JsonMapper.shared()) {})
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Map.Entry<String, String> entry(String key, String value) {
    return new java.util.AbstractMap.SimpleEntry<>(key, value);
  }

  static final class UuidListConverter extends JsonListConverter<UUID> {
    UuidListConverter() {
      super(UUID.class);
    }
  }
}
