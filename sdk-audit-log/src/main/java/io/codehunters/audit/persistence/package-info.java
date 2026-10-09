/**
 * How audit events are written: the JDBC adapter and the publishers that wrap it (asynchronous,
 * fail-safe, transaction-aware, {@code REQUIRES_NEW}, JSON snapshot).
 */
package io.codehunters.audit.persistence;
