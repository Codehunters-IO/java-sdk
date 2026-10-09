/**
 * A framework-free way to say "the client sent something this code cannot accept": {@link
 * io.codehunters.commons.error.ClientInputException} carries a stable code and a message that is
 * safe to show to the client. A web layer such as sdk-web answers it with 400.
 */
package io.codehunters.commons.error;
