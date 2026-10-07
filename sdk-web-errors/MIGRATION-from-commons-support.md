# Migrating from commons-web (codehuntersio-java-commons-support)

`io.codehunters.commons:commons-web` 5.1.0 is replaced by `io.codehunters:sdk-web-errors` 1.0.0. Every
class was rewritten; none is source compatible.

## Error responses

| commons-web | sdk-web-errors |
|---|---|
| `ApiError` / `ApiMessage` / `Api` (own JSON envelope with `code`, `message`, `timestamp`, `trace`, `errors`) | RFC 9457 `ProblemDetail` built by `WebErrorsExceptionHandler`: `code`, `detail`, `instance`, `traceId`, `errors` |
| `ApiError.trace` (the serialized `Throwable`) | Dropped. The exception is logged with the `traceId`; the client gets the `traceId` only |
| `timestamp` (`dd-MM-yyyy hh:mm:ss`, 12-hour, server time zone) | Dropped. Use the response `Date` header or the log, keyed by `traceId` |
| `ApiMessage.data` (success envelope) | Dropped. Return the resource itself |
| No global handler: every service wrote its own `@ControllerAdvice` | Auto-configured handler; services keep an advice only for exceptions they answer differently |

## Exceptions

| commons-web | sdk-web-errors |
|---|---|
| `ContractException(message, code, HttpStatus, errors)` | `ApiException(ErrorCode, arguments)`; the code enum carries the status |
| `RestException(message, HttpStatus, errors)` (code fixed to `API-ERR`) | `ApiException` with a code of your own; validation errors are produced by the handler |
| The free-text `message` sent to the client | A `MessageSource` key per code (`error.<CODE>`), localized; the exception message is the code only |
| `errors` (`List<? extends Serializable>`, often `ObjectError`s with rejected values) | `errors` of `FieldViolation(field, code, message)`, never the rejected value |

## Dropped, with their replacement

| commons-web | Replacement |
|---|---|
| `AbstractRestControllerSupport.createURI(path, id)` | `ServletUriComponentsBuilder.fromCurrentRequest().path(path).buildAndExpand(id).toUri()` |
| `AbstractRestControllerSupport.defaultBindingResultHasErrors` | `@Valid` on the parameter; the handler answers `VALIDATION_FAILED` |
| `PaginationRequest` / `PaginationResponse` | `Pageable` and a page DTO; a page response type is planned for `sdk-jpa` |
| `@UUIDConstraint` / `UUIDValidator` | Hibernate Validator's `@UUID`, or a `UUID` parameter type |
| `CollectionValidator` | Container element constraints: `List<@Valid Item>` |
