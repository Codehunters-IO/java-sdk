/**
 * The aspect that turns {@link io.codehunters.audit.Auditable} calls into audit events, with its
 * SpEL evaluation, MDC capture and proxy check. Wired by the auto-configuration; not API; may
 * change in any release.
 */
package io.codehunters.audit.internal.aspect;
