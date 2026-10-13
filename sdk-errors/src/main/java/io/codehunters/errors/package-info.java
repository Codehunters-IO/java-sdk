/**
 * The error contract shared by the SDKs and the services that use them. {@link
 * io.codehunters.errors.ClientInputException} says "the client sent something this code cannot
 * accept", with a stable code and a message that is safe to show to the client. sdk-web answers it
 * with 400; sdk-jpa and sdk-barcode throw subclasses of it.
 *
 * <p>Framework-free and dependency-free on purpose: a library that only needs to reject input
 * depends on this module, not on a toolbox of unrelated helpers.
 */
package io.codehunters.errors;
