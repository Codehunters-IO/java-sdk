package io.codehunters.jpa.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.jpa.fixture.ShippingAddress;
import io.codehunters.jpa.fixture.ShippingAddressConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class JsonAttributeConverterTest {

  private static final ShippingAddress ADDRESS = new ShippingAddress("Calle 1", "Bogota", 3);

  private final ShippingAddressConverter converter = new ShippingAddressConverter();

  @Test
  void should_write_a_value_as_json_and_read_it_back() {
    var json = converter.convertToDatabaseColumn(ADDRESS);

    assertThat(json).isEqualTo("{\"street\":\"Calle 1\",\"city\":\"Bogota\",\"floor\":3}");
    assertThat(converter.convertToEntityAttribute(json)).isEqualTo(ADDRESS);
  }

  @Test
  void should_store_null_as_a_null_column() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "null"})
  void should_read_an_empty_column_as_null(String column) {
    assertThat(converter.convertToEntityAttribute(column)).isNull();
  }

  @Test
  void should_ignore_properties_the_type_no_longer_has() {
    var json = "{\"street\":\"Calle 1\",\"city\":\"Bogota\",\"floor\":3,\"zip\":\"110111\"}";

    assertThat(converter.convertToEntityAttribute(json)).isEqualTo(ADDRESS);
  }

  @Test
  void should_report_unreadable_json_without_its_content_or_cause() {
    assertThatThrownBy(() -> converter.convertToEntityAttribute("{\"street\": secret-value"))
        .isInstanceOf(JsonColumnException.class)
        .hasMessageContaining("ShippingAddress")
        .hasMessageNotContaining("secret-value")
        .hasNoCause();
  }

  @Test
  void should_report_a_value_jackson_cannot_write() {
    var converter = new OpaqueConverter();

    assertThatThrownBy(() -> converter.convertToDatabaseColumn(new Opaque()))
        .isInstanceOf(JsonColumnException.class)
        .hasMessageStartingWith("Could not write")
        .hasNoCause();
  }

  @Test
  void should_use_the_mapper_it_is_given() {
    var snakeCase =
        JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    var custom = new NamedConverter(snakeCase);

    assertThat(custom.convertToDatabaseColumn(new Named("Ada Lovelace")))
        .isEqualTo("{\"full_name\":\"Ada Lovelace\"}");
  }

  @Test
  void should_reject_a_missing_type_or_mapper() {
    assertThatThrownBy(() -> new NamedConverter(null)).isInstanceOf(IllegalArgumentException.class);
  }

  record Named(String fullName) {}

  static final class Opaque {
    public String getSecret() {
      throw new IllegalStateException("not readable");
    }
  }

  static final class OpaqueConverter extends JsonAttributeConverter<Opaque> {
    OpaqueConverter() {
      super(new TypeReference<>() {});
    }
  }

  static final class NamedConverter extends JsonAttributeConverter<Named> {
    NamedConverter(JsonMapper mapper) {
      super(new TypeReference<>() {}, mapper);
    }
  }
}
