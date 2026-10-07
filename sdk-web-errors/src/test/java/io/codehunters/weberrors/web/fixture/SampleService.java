package io.codehunters.weberrors.web.fixture;

import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

/** A service validated through Spring's method validation proxy. */
@Validated
public class SampleService {

  /**
   * Accepts a valid order.
   *
   * @param order the order
   * @return a confirmation
   */
  public String create(@Valid CreateOrder order) {
    return "created";
  }
}
