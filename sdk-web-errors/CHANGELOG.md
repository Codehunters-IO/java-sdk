# Changelog — sdk-web-errors

## 1.0.0

First release ([ADR 0011](../docs/adr/0011-problem-details-with-stable-error-codes.md)).

- `WebErrorsExceptionHandler`: answers `ApiException`, Spring MVC's own exceptions, bean validation
  failures, `@ResponseStatus` exceptions and anything unmapped with an RFC 9457 problem detail
  (`application/problem+json`) carrying `code`, a localized `detail`, `instance` and `traceId`.
- Validation failures list every violated constraint in `errors` (`field`, `code`, `message`),
  never the rejected value.
- Unmapped exceptions answer a bare 500 `INTERNAL_ERROR`; the exception is logged at `ERROR` with
  the trace id, never sent to the client.
- Spring Security's access and authentication exceptions are rethrown for Spring Security to answer.
- `ErrorCode`, `ApiException`, `FieldViolation` and `CommonErrorCode` have no framework dependency;
  an ArchUnit test enforces it.
- Replaces Boot's problem-details handler; backs off for an application's own
  `ResponseEntityExceptionHandler` and with `codehunters.web-errors.enabled=false`.
- `codehunters.web-errors.type-base-uri` and `include-trace-id`, validated at startup.
- Replaces `commons-web` from `codehuntersio-java-commons-support`; see
  [MIGRATION-from-commons-support.md](MIGRATION-from-commons-support.md).
