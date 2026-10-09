package io.codehunters.web.errors.servlet.fixture;

import jakarta.validation.constraints.Pattern;

/**
 * A request body whose constraint message echoes the rejected value.
 *
 * @param code the coupon code
 */
public record Coupon(
    @Pattern(regexp = "[A-Z]{3}", message = "${validatedValue} is not a coupon") String code) {}
