package io.codehunters.geo;

/**
 * An immutable (lat, lon) coordinate.
 *
 * @param latitude the point's latitude
 * @param longitude the point's longitude
 */
public record GeoPoint(Latitude latitude, Longitude longitude) {}
