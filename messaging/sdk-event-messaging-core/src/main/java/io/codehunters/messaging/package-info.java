/**
 * Event-messaging SDK public API: the {@link io.codehunters.messaging.OutboxEvent} write command,
 * the {@link io.codehunters.messaging.EventMessage} envelope, the {@link
 * io.codehunters.messaging.OutboxMessage} row, its status, and the ports an application calls
 * ({@link io.codehunters.messaging.OutboxWriter}, {@link
 * io.codehunters.messaging.SendOutboxMessagesPort}) or a store or a transport implements ({@link
 * io.codehunters.messaging.OutboxDispatchStore}, {@link
 * io.codehunters.messaging.MessagePublisherPort}).
 */
package io.codehunters.messaging;
