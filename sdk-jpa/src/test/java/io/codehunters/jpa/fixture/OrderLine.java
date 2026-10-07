package io.codehunters.jpa.fixture;

import io.codehunters.jpa.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_lines")
public class OrderLine extends BaseEntity {

  @Column(name = "sku")
  private String sku;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id")
  private Order order;

  protected OrderLine() {}

  public OrderLine(String sku, Order order) {
    this.sku = sku;
    this.order = order;
  }
}
