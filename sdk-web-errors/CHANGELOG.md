# Changelog — sdk-web-errors

## 1.0.0

First release ([ADR 0011](../docs/adr/0011-problem-details-with-stable-error-codes.md)).

- `WebErrorsExceptionHandler`: answers `ApiException`, Spring MVC's own exceptions, bean validation
  failures, `@ResponseStatus` exceptions and anything unmapped with an RFC 9457 problem detail
  (`application/problem+json`) carrying `code`, a localized `detail`, `instance` and `traceId`.
- Validation failures list every violated constraint in `errors` (`field`, `code`, `message`),
  never the rejected value. Messages come from the bundles, from a constraint message whose template
  has no expression, or from a fixed fallback; Spring's default messages, which echo the input, are
  never rendered.
- Constraint violations that do not concern method arguments, and failed return-value validation,
  answer 500 `INTERNAL_ERROR` without `errors`.
- Unmapped exceptions answer a bare 500 `INTERNAL_ERROR`; the exception is logged at `ERROR` with
  the trace id, never sent to the client.
- Spring Security's access and authentication exceptions, also when wrapped, are rethrown for Spring
  Security to answer.
- `ProblemErrorController` replaces Boot's `BasicErrorController` for the container's error dispatch.
- With Spring Security: `ProblemAuthenticationEntryPoint` and `ProblemAccessDeniedHandler` for the
  application's filter chain, keeping the OAuth2 bearer challenge; `ProblemWriter` for other servlet
  code.
- 5xx answers are exposed to the observation filter. Committed responses are left alone and logged
  once at `WARN`; disconnected clients are logged at `DEBUG`; async timeouts at `WARN` without a
  stack trace. Log lines keep the trace id even when responses leave it out.
- `instance` is the originally requested path without path parameters.
- `ApiException` requires a non-blank code and refuses serialization.
- `ErrorCode`, `ApiException`, `FieldViolation` and `CommonErrorCode` have no framework dependency;
  an ArchUnit test enforces it.
- Replaces Boot's problem-details handler; backs off for an application's own
  `ResponseEntityExceptionHandler` and with `codehunters.web-errors.enabled=false`.
- `codehunters.web-errors.type-base-uri` and `include-trace-id`, validated at startup.
- Replaces `commons-web` from `codehuntersio-java-commons-support`; see
  [MIGRATION-from-commons-support.md](MIGRATION-from-commons-support.md).
