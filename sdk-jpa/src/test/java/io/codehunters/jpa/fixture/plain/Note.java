package io.codehunters.jpa.fixture.plain;

import io.codehunters.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** An entity without auditing columns: the SDK has nothing to warn about. */
@Entity
@Table(name = "notes")
public class Note extends BaseEntity {

  @Column(name = "body")
  private String body;

  protected Note() {}

  public Note(String body) {
    this.body = body;
  }
}
