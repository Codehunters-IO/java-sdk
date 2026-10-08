package io.codehunters.weberrors.web.fixture;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request body of the sample application.
 *
 * @param name the order name
 * @param quantity the ordered quantity
 */
public record CreateOrder(@NotBlank String name, @Positive int quantity) {}
