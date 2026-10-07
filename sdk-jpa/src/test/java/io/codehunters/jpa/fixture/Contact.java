package io.codehunters.jpa.fixture;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Contact {

  @Column(name = "email")
  private String email;

  protected Contact() {}

  public Contact(String email) {
    this.email = email;
  }
}
