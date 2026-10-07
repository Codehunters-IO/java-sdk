package io.codehunters.jpa.fixture;

import io.codehunters.jpa.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {

  @Column(name = "name")
  private String name;

  protected Customer() {}

  public Customer(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void rename(String name) {
    this.name = name;
  }
}
