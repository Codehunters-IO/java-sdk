# sdk-web-errors

One error format for every Spring MVC service: [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)
problem details with a stable error `code`, a localized `detail`, the `traceId` and, for validation
failures, the list of violated constraints. Rejected values, exception messages and stack traces
never reach the client.

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
| `instance` | the request path, without the query string |
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
| `MethodArgumentNotValidException` (`@Valid @RequestBody`) | 400 | `VALIDATION_FAILED` + `errors` |
| `HandlerMethodValidationException` (constraints on `@RequestParam`, `@PathVariable`…) | 400 | `VALIDATION_FAILED` + `errors` |
| `jakarta.validation.ConstraintViolationException` (`@Validated` services) | 400 | `VALIDATION_FAILED` + `errors` |
| Spring MVC's own exceptions (404, 405, 406, 415, type mismatch, unreadable body…) | Spring's status | the status name, e.g. `NOT_FOUND`, `METHOD_NOT_ALLOWED` |
| An exception annotated with `@ResponseStatus` | the annotated status | the status name; `reason` becomes the detail |
| `ResponseStatusException` thrown by the application | its status | the status name; its reason becomes the detail |
| Spring Security's `AccessDeniedException` / `AuthenticationException` | rethrown | Spring Security answers them |
| Anything else | 500 | `INTERNAL_ERROR` |

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

Violation messages are the validator's interpolated messages, so `{jakarta.validation...}` and custom
`{key}` messages follow the same bundles.

## Logging

- 5xx: `ERROR` with the status, code, trace id and the exception, stack trace included. This is the
  only place the exception message appears.
- 4xx: `DEBUG` with the status, code and trace id, never the arguments or rejected values.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `codehunters.web-errors.enabled` | `true` | Turns the SDK off |
| `codehunters.web-errors.type-base-uri` | — | Absolute base URI ending with `/` for `type`; startup fails otherwise |
| `codehunters.web-errors.include-trace-id` | `true` | Adds the `traceId` member |

## Precedence and back-off

- The handler is ordered last. An application `@RestControllerAdvice` that handles an exception wins
  over it for that exception.
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
- A default detail containing an apostrophe is formatted with `MessageFormat` when the exception has
  arguments, which drops the apostrophe; define the message in the bundle instead.
