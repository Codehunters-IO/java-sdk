package io.codehunters.jpa.fixture;

import io.codehunters.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {

  @Column(name = "name")
  private String name;

  @Embedded private Contact contact;

  @OneToMany(mappedBy = "customer")
  private List<Order> orders = new ArrayList<>();

  protected Customer() {}

  public Customer(String name) {
    this.name = name;
  }

  public Customer(String name, Contact contact) {
    this.name = name;
    this.contact = contact;
  }

  public String getName() {
    return name;
  }

  public void rename(String name) {
    this.name = name;
  }
}
