package io.codehunters.parameter.domain.model;

/**
 * Owning vitxo-ms module for a parameter. Mirrors the server-side enum without depending on
 * vitxo-ms-managment.
 */
public enum ParameterModule {
  GENERAL,
  AUTH,
  CONFIG_SERVER,
  FILE_SHARE,
  MANAGEMENT,
  NOTIFICATIONS,
  PAYMENT,
  RAFFLES
}
