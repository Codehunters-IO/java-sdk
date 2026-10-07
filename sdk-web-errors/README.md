# sdk-web-errors

One error format for every Spring MVC service: [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)
problem details with a stable error `code`, a localized `detail`, the `traceId` and, for validation
failures, the list of violated constraints. Rejected values, exception messages and stack traces
never reach the client.

It covers exceptions handled by Spring MVC, the servlet container's error dispatch, and — once wired
into the filter chain — Spring Security's 401 and 403 (see
[Errors outside Spring MVC](#errors-outside-spring-mvc)).

```kotlin
implementation(platform("io.codehunters:sdk-bom:<version>"))
implementation("io.codehunters:sdk-web-errors")
```

## Response shape

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{
  "type": "https://errors.example.com/order-not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Order 42 was not found",
  "instance": "/orders/42",
  "code": "ORDER_NOT_FOUND",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

Validation failures answer `400` with `code` `VALIDATION_FAILED` and an `errors` array, sorted by
field then constraint:

```json
"errors": [
  { "field": "name", "code": "NotBlank", "message": "must not be blank" },
  { "field": "quantity", "code": "Positive", "message": "must be greater than 0" }
]
```

| Member | Source |
|---|---|
| `type` | `codehunters.web-errors.type-base-uri` + the code in kebab case; omitted (`about:blank`) without a base |
| `title` | the status reason phrase |
| `detail` | the localized message (see [Messages](#messages)); the reason phrase when none is defined |
| `instance` | the requested path, without the query string or path parameters (`;jsessionid=…`); path variables are echoed |
| `code` | the stable error code |
| `traceId` | the current trace id; omitted when no trace is active |
| `errors` | one entry per violated constraint: `field`, `code` (constraint name), `message` |

## Throwing errors

Define one enum per bounded context. `ErrorCode`, `ApiException` and `FieldViolation` have no Spring
dependency, so domain and application code can throw them.

```java
enum OrderErrorCode implements ErrorCode {
  ORDER_NOT_FOUND(404),
  ORDER_ALREADY_PAID(409);

  private final int status;

  OrderErrorCode(int status) { this.status = status; }

  @Override public String code() { return name(); }
  @Override public int status() { return status; }
}

throw new ApiException(OrderErrorCode.ORDER_NOT_FOUND, List.of(orderId));
```

- The code is part of the API contract. Never rename or reuse one once released.
- A status outside 400–599 is answered with 500.
- The exception message is the code alone, so logs never contain the arguments. The arguments fill
  the placeholders of the detail and are sent to the client: never pass secrets or personal data.
- `new ApiException(code, arguments, cause)` keeps the cause for the server log only.

## What is mapped

| Exception | Status | `code` |
|---|---|---|
| `ApiException` | the code's status | the code |
| `MethodArgumentNotValidException` (`@Valid @RequestBody`, `@ModelAttribute` binding) | 400 | `VALIDATION_FAILED` + `errors` |
| `HandlerMethodValidationException` (constraints on `@RequestParam`, `@PathVariable`…) | 400 | `VALIDATION_FAILED` + `errors` |
| `HandlerMethodValidationException` for a controller's return value | 500 | `INTERNAL_ERROR`, no `errors` |
| `jakarta.validation.ConstraintViolationException` whose violations all concern method arguments (`@Validated` services) | 400 | `VALIDATION_FAILED` + `errors` |
| `jakarta.validation.ConstraintViolationException` for an entity or a return value | 500 | `INTERNAL_ERROR`, no `errors` |
| Spring MVC's own exceptions (404, 405, 406, 415, type mismatch, unreadable body…) | Spring's status | the status name, e.g. `NOT_FOUND`, `METHOD_NOT_ALLOWED` |
| An exception annotated with `@ResponseStatus` | the annotated status | the status name; `reason` becomes the detail |
| `ResponseStatusException` thrown by the application | its status | the status name; its reason becomes the detail |
| Spring Security's `AccessDeniedException` / `AuthenticationException`, also when wrapped by a proxy or future | rethrown | Spring Security answers them |
| A disconnected client (broken pipe, connection reset) | none | logged at `DEBUG` |
| Anything else | 500 | `INTERNAL_ERROR` |

A response already committed (a stream or `SseEmitter` that failed midway) is left alone: the
failure is logged once at `WARN`, without the exception message or stack trace.

Headers Spring sets on its own errors are kept, such as `Allow` on a 405.

The detail of Spring MVC's own exceptions is replaced by the reason phrase (or the `web-errors.*`
message): Spring's details echo request input, such as the value that failed a type conversion. For
the same reason a Spring `ErrorResponseException` subclass, or an `ErrorResponseException` the
application builds, loses its custom detail and properties; throw `ApiException` instead.

For a `@Validated` service, the `field` of a constraint violation is Bean Validation's property path,
which starts with the method and parameter (`create.order.name`).

## Messages

Details are resolved through the application's `MessageSource` (the `sdk-i18n` bundles when that
SDK is present), in the request locale:

| Error | Key | Arguments |
|---|---|---|
| `ApiException` | `ErrorCode.messageKey()`, by default `error.<CODE>` | the exception's arguments |
| Validation failure | `error.VALIDATION_FAILED` | — |
| Unmapped exception | `error.INTERNAL_ERROR` | — |
| Spring MVC exception, `@ResponseStatus` | `web-errors.<STATUS_NAME>`, e.g. `web-errors.NOT_FOUND` | — |

```properties
error.ORDER_NOT_FOUND=Order {0} was not found
web-errors.NOT_FOUND=Resource not found
```

A violation's `message` is resolved in this order, and a default message is never formatted with
`MessageFormat`:

1. The bundles, through the error's codes (`NotBlank.order.name`, …, `NotBlank`; `typeMismatch.quantity`,
   …, `typeMismatch` for a failed conversion).
2. The validator's interpolated message, unless its template uses an expression such as
   `${validatedValue}`, which puts the rejected value into the message.
3. `web-errors.typeMismatch` for a failed conversion, `web-errors.invalid` otherwise, both defaulting
   to `Invalid value`.

Spring's own default message for a failed conversion contains the input and Java type names, so it
is never used.

Arguments of an `ApiException` are formatted by `MessageFormat`, which groups numbers by locale
(`1234` becomes `1,234`). Use `{0,number,#}` in the message, or pass identifiers as strings.

## Errors outside Spring MVC

**Error dispatch.** `ProblemErrorController` replaces Boot's `BasicErrorController` and answers the
servlet container's error dispatch (`response.sendError`, exceptions thrown by filters, errors raised
by the container) with a problem for the dispatched status, as `application/problem+json` whatever
the `Accept` header. Boot's HTML whitelabel page is therefore gone. It backs off when the application
declares its own `ErrorController`.

**Spring Security.** Spring Security answers 401 and 403 in its filters, before Spring MVC. With
Spring Security on the classpath the SDK offers a `ProblemAuthenticationEntryPoint` and a
`ProblemAccessDeniedHandler`; with the OAuth2 resource server they keep its bearer challenge
(`WWW-Authenticate: Bearer …`). Wire them into the filter chain:

```java
@Bean
SecurityFilterChain api(
    HttpSecurity http,
    ProblemAuthenticationEntryPoint entryPoint,
    ProblemAccessDeniedHandler accessDenied) throws Exception {
  return http
      .oauth2ResourceServer(server -> server
          .jwt(Customizer.withDefaults())
          .authenticationEntryPoint(entryPoint)
          .accessDeniedHandler(accessDenied))
      .exceptionHandling(handling -> handling
          .authenticationEntryPoint(entryPoint)
          .accessDeniedHandler(accessDenied))
      .build();
}
```

The filter chain of `sdk-user-security` does not wire them yet; applications that need problem
details for 401/403 declare their own chain as above. `ProblemWriter` writes the same body from any
other servlet code.

## Logging

- 5xx: `ERROR` with the status, code, trace id and the exception, stack trace included. This is the
  only place the exception message appears. The exception is also exposed to the observation
  filter, so `http.server.requests` and the server span record it.
- Async request timeouts (503): `WARN` without the stack trace.
- 4xx: `DEBUG` with the status, code and trace id, never the arguments or rejected values. When an
  `ApiException` has a cause, the exception and its cause are logged at `DEBUG`.
- Log lines carry the trace id even with `include-trace-id=false`.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `codehunters.web-errors.enabled` | `true` | Turns the SDK off |
| `codehunters.web-errors.type-base-uri` | — | Absolute base URI ending with `/` for `type`; startup fails otherwise |
| `codehunters.web-errors.include-trace-id` | `true` | Adds the `traceId` member |

## Precedence and back-off

- The handler is ordered last. An application `@RestControllerAdvice` that handles an exception wins
  over it for that exception. An advice without `@Order` has the same order; it still wins because
  application beans are registered before auto-configured ones. Give an advice contributed by
  another auto-configuration an explicit `@Order`.
- It runs before Boot's Web MVC auto-configuration, so Boot's own problem-details handler
  (`spring.mvc.problemdetails.enabled`) is not registered.
- The SDK backs off when the application declares its own `ResponseEntityExceptionHandler` bean, and
  outside servlet web applications.
- The trace id comes from the tracing SDK's `TraceContextPort` when that bean exists, otherwise from
  the `traceId` MDC key (where Micrometer Tracing also puts it). Declare a `TraceIdProvider` bean to
  supply it differently.

## Known limits

- Servlet stack only; WebFlux is not supported.
- `field` of a method parameter violation is the Java parameter name, which needs `-parameters`
  (Spring Boot's Gradle and Maven plugins enable it); without it the field is `argN`.
- `ApiException` cannot be serialized: its code and arguments are not required to be.
