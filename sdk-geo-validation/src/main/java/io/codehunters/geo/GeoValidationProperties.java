package io.codehunters.geo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tuning for geo validation, bound from {@code geo.validation.*}.
 *
 * @param enabled whether the auto-configured {@link RaffleGeoValidationPort} bean is registered
 */
@ConfigurationProperties(prefix = "geo.validation")
public record GeoValidationProperties(@DefaultValue("true") boolean enabled) {}
