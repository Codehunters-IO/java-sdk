---
status: accepted
date: 2026-10-07
decision-makers: [andres]
---

# 0011. Answer errors with problem details and stable error codes

## Context and Problem Statement

Each service writes its own `@ControllerAdvice`, or uses the old `commons-web` envelope (`ApiError`)
that serializes the whole `Throwable`, formats timestamps in the server's time zone and sends free
text as the only identifier. Clients cannot branch on an error reliably, messages are not localized,
and stack traces and rejected values reach responses. Which error format should every Spring MVC
service share, and how does code signal an error?

## Decision Drivers

- Clients need a machine-readable identifier that survives message rewording and translation.
- Nothing sensitive in responses: no stack trace, exception message or rejected value.
- Domain code must be able to raise errors without depending on Spring.
- Applications must stay able to answer specific exceptions their own way.

## Considered Options

- Option A — RFC 9457 `ProblemDetail` with a `code` extension from an `ErrorCode` enum per bounded
  context, produced by an auto-configured, last-ordered `ResponseEntityExceptionHandler`.
- Option B — Keep a custom JSON envelope like `ApiError`, rewritten without the leaks.
- Option C — Boot's built-in problem details (`spring.mvc.problemdetails.enabled`) with no SDK.

## Decision Outcome

Chosen option: **Option A**. It is the standard Spring 7 already speaks (`application/problem+json`,
`ErrorResponse`), so framework errors and application errors share one shape, while the `code`
extension gives clients the stable identifier that `title` and `detail` cannot.

- `ErrorCode`, `ApiException` and `FieldViolation` live in a package with no framework dependency,
  enforced by ArchUnit; the status is an `int`.
- The handler is ordered last and backs off for an application's own
  `ResponseEntityExceptionHandler`.
- Details are localized through the `MessageSource`; framework details are replaced because they
  echo request input. Violation messages come from the bundles or from input-free defaults only:
  rendering Spring's defaults through `MessageFormat` both leaked the input and cached every
  distinct value.
- The same problem format covers what never reaches Spring MVC: a problem-detail error controller
  replaces Boot's, and Spring Security entry points are offered for the application's filter chain.

### Consequences

- Good, because every service answers errors the same way and clients branch on `code`.
- Good, because leaks are prevented in one place and covered by tests.
- Bad, because codes become API contract: renaming one is a breaking change.
- Bad, because an `ErrorResponseException` carrying custom details loses them; applications use
  `ApiException` instead.
- Bad, because Boot's HTML whitelabel error page is replaced, and 401/403 are problem details only
  where the application wires the SDK's entry points into its filter chain.

## Pros and Cons of the Options

### Option B — Custom envelope

- Good, because it matches what some clients already parse.
- Bad, because Spring's own errors would need translating into it, and every tool that understands
  problem details would not understand it.

### Option C — Boot's problem details only

- Good, because it needs no code.
- Bad, because it has no stable code, no validation `errors` list, and Spring's details echo input
  (a failed conversion includes the rejected value).
