package io.codehunters.jpa.fixture;

import io.codehunters.jpa.converter.StringListConverter;
import io.codehunters.jpa.converter.StringMapConverter;
import io.codehunters.jpa.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "orders")
public class Order extends AuditableEntity {

  @Column(name = "reference")
  private String reference;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private OrderStatus status;

  @Column(name = "amount")
  private BigDecimal amount;

  @Column(name = "placed_at")
  private Instant placedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id")
  private Customer customer;

  @Convert(converter = StringListConverter.class)
  @Column(name = "tags", length = 2000)
  private List<String> tags;

  @Convert(converter = StringMapConverter.class)
  @Column(name = "attributes", length = 2000)
  private Map<String, String> attributes;

  @Convert(converter = ShippingAddressConverter.class)
  @Column(name = "shipping_address", length = 2000)
  private ShippingAddress shippingAddress;

  protected Order() {}

  public Order(String reference) {
    this.reference = reference;
    this.status = OrderStatus.OPEN;
  }

  public Order(
      String reference,
      OrderStatus status,
      BigDecimal amount,
      Instant placedAt,
      Customer customer) {
    this.reference = reference;
    this.status = status;
    this.amount = amount;
    this.placedAt = placedAt;
    this.customer = customer;
  }

  public String getReference() {
    return reference;
  }

  public Customer getCustomer() {
    return customer;
  }

  public void changeReference(String reference) {
    this.reference = reference;
  }

  public List<String> getTags() {
    return tags;
  }

  public Map<String, String> getAttributes() {
    return attributes;
  }

  public ShippingAddress getShippingAddress() {
    return shippingAddress;
  }

  public void describe(
      List<String> tags, Map<String, String> attributes, ShippingAddress shippingAddress) {
    this.tags = tags;
    this.attributes = attributes;
    this.shippingAddress = shippingAddress;
  }
}
