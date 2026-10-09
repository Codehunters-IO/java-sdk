package io.codehunters.web.errors.servlet.fixture;

/**
 * Query parameters bound through constructor binding, so a conversion failure is a binding error.
 *
 * @param quantity the quantity filter
 */
public record OrderFilter(int quantity) {}
