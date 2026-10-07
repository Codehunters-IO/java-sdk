package io.codehunters.jpa.fixture;

import io.codehunters.jpa.converter.JsonAttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;

@Converter
public class ShippingAddressConverter extends JsonAttributeConverter<ShippingAddress> {

  public ShippingAddressConverter() {
    super(new TypeReference<>() {});
  }
}
