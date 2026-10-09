# 0018 — Charge reclaim attempts only for rows whose send had started

- Status: accepted
- Date: 2026-10-08

## Context

The outbox dispatcher claims a batch of rows (`IN_FLIGHT`, `claimed_at`, `claimed_by`) and
publishes them one by one; each publish blocks until the broker confirms. When a dispatcher crashes
or stalls past `claim.ttl-ms`, another instance reclaims its rows. Up to 6.x the claim added one
attempt to every reclaimed row, because the previous owner might have published it before dying.

The table did not record which rows of a batch had been tried, so rows the dead dispatcher never
reached were charged too. With a low `max-attempts`, a few crashes (pod evictions, deploys during a
large backlog) could mark rows `FAILED` that had never been sent. MIGRATION-6.0 listed this as a
known limit to fix in 7.0.

Options considered:

1. **Count attempts in the use case only, never on reclaim.** Loses the poison-message guard: a row
   that crashes the dispatcher while being sent would be reclaimed forever.
2. **Charge on reclaim only the first row of the batch.** Wrong whenever the crash happens after the
   first publish; the dispatcher does not stop at the first row.
3. **Record, per row, that its send started** (`send_started_at`), and charge on reclaim only rows
   that have it.

## Decision

Option 3.

- `OutboxRepositoryPort.markSendStarted(message)` writes `send_started_at` right before the publish
  of that row, fenced by `claimed_by` and `status = 'IN_FLIGHT'`. It returns `false` when the claim
  was taken over; the use case then neither publishes nor updates the row.
- The claim of every dialect charges `attempts + 1` only for `IN_FLIGHT` rows with
  `send_started_at` set, and clears `send_started_at` on every claimed row.
- Updates that end a claim clear `send_started_at`.
- The `-core`, `-jdbc`, `-rabbit` and `-kafka` artifacts move to 7.0.0 together: the port change
  breaks custom adapters, and the claim needs the new column.
- The `outbox.*` property aliases, due to be removed in 7.0, stay until 8.0: no service has migrated
  off 5.x yet, and those services will move straight to 7.0.

## Consequences

- Rows a crashed dispatcher never reached keep their attempts; the poison-message guard keeps
  working for the rows it was actually sending.
- One extra primary-key `UPDATE` per published row. Publishing already costs a broker round trip
  and an `UPDATE` per row, so dispatch throughput drops by roughly one more database round trip
  per message.
- A lost claim is detected before the send instead of only at the final, fenced update, so fewer
  duplicate sends after a takeover. Delivery stays at-least-once: a crash after the broker accepted
  a row and before its update still sends it twice.
- Services must add `send_started_at` before deploying 7.0; the column is nullable and ignored by
  6.x, so adding it early is safe.
- The mark is fenced only when it is written. A publish that blocks past the rest of the claim TTL
  can still be sent by two dispatchers; at-least-once delivery and consumer deduplication on
  `outbox-message-id` still apply.
- The mark must commit before the publish, so dispatch must run outside a caller transaction. The
  adapter warns once when it does not; a failed mark skips only its row.
- SQL Server integration tests run the reference DDL and the 7.0 upgrade script on SQL Server 2022
  on x86-64 hosts (CI). ARM hosts, which have no SQL Server image, use azure-sql-edge 1.0.7:
  Microsoft retired Azure SQL Edge on 2025-09-30, so that image is final and may drift from the
  engines services run.
