# Migrating to sdk-web 1.0

`io.codehunters:sdk-web` 1.0.0 is one artifact for what were `sdk-web-errors`, `sdk-device` and
`sdk-i18n` ([ADR 0016](../docs/adr/0016-consolidate-web-and-commons-artifacts.md)). Pick the section
for where the service comes from.

## From sdk-web-errors, sdk-device or sdk-i18n 1.0.0

Only the coordinates change. Packages, classes, properties, legacy aliases and behaviour are those of
the 1.0.0 releases.

| Before | Now |
|---|---|
| `io.codehunters:sdk-web-errors:1.0.0` | `io.codehunters:sdk-web:1.0.0` |
| `io.codehunters:sdk-device:1.0.0` | `io.codehunters:sdk-web:1.0.0` |
| `io.codehunters:sdk-i18n:1.0.0` | `io.codehunters:sdk-web:1.0.0` |

1. **Dependency.** Replace every one of them with a single `sdk-web`, or import `sdk-bom` 1.2.0 and
   declare `io.codehunters:sdk-web` without a version.
2. **Gradle.** `sdk-web` declares the capabilities `io.codehunters:sdk-web-errors`,
   `io.codehunters:sdk-device` and `io.codehunters:sdk-i18n`. If another library still brings one of
   them, the build fails with a capability conflict instead of loading the classes twice: upgrade
   that library or resolve it with `capabilitiesResolution`.
3. **Maven.** Maven does not check capabilities. Add an `<exclusion>` for the three old artifacts to
   any dependency that still brings them, or the same classes end up on the classpath twice.
4. **Parts you did not use before.** A service that had only one of the three now also gets the
   other two auto-configurations. Turn off what you do not want:

   ```yaml
   codehunters:
     web-errors:
       enabled: false # problem-detail handler, error controller, security handlers
     device:
       enabled: false # ClientIpResolver, UserAgentParser, ClientContextResolver
     i18n:
       enabled: false # messageSource, localeResolver, locale MDC filter
   ```

   Errors and the i18n locale resolver only activate in servlet web applications. The i18n
   `messageSource` is registered in any application unless a bean named `messageSource` exists.

## From sdk-i18n 0.x

First do the coordinate swap above, then:

1. **Properties.** Rename `vitxo.i18n.*` to `codehunters.i18n.*`. The old keys keep working
   until 2.0.0, and startup logs which ones it found.

   | 0.x | 1.0 |
   |---|---|
   | `vitxo.i18n.basename` | `codehunters.i18n.basename` (now a list) |
   | `vitxo.i18n.default-locale` | `codehunters.i18n.default-locale` |
   | `vitxo.i18n.header-name` | `codehunters.i18n.header-name` |
   | `vitxo.i18n.encoding` | `codehunters.i18n.encoding` |
   | `vitxo.i18n.fallback-to-system-locale` | `codehunters.i18n.fallback-to-system-locale` |

2. **`I18nProperties` in code.** It is a record: replace `getBasename()` with `basename()` (a
   `List<String>`), `getEncoding()` with `encoding()` (a `Charset`), and so on. The setters are
   gone; set values through configuration.
3. **Startup validation.** Check that the header name is a plain header token, the base names are
   not blank, and the locales name a language. An application that set `default-locale` to an empty
   value now fails to start.
4. **Non-web applications.** Batch jobs and listeners no longer get a `localeResolver` or the MDC
   filter. If such an application injected `LocaleResolver`, declare the bean yourself.
5. **Custom message sources.** The SDK's `messageSource` now backs off only for a bean named
   `messageSource`. If you declared a `MessageSource` under another name to replace the SDK's, rename
   it to `messageSource`.
6. **Validator.** The SDK no longer registers `validatorFactoryBean`. Boot's `defaultValidator`
   resolves the same `{key}` messages through the SDK's message source, so constraint messages are
   unchanged when `spring-boot-starter-validation` is on the classpath. If code injected the bean by
   the name `validatorFactoryBean`, inject `Validator` instead.
7. **`spring.messages.*` and `spring.web.locale*`.** Still ignored while the SDK provides the message
   source and the locale resolver; startup now warns about them. Move the settings to
   `codehunters.i18n.*`.
8. **`Accept-Language` fallbacks.** With no `supported-locales`, a header of `*`, one where every
   range has weight zero, or an unparseable one now resolves to `default-locale` instead of the
   container's root or JVM locale.
9. **Restricting locales (optional).** Set `codehunters.i18n.supported-locales` to stop serving
   locales you have no bundles for; it must include the default locale.

## From codehuntersio-java-commons-support

### `commons-web`

`io.codehunters.commons:commons-web` 5.1.0 is replaced by the errors part of `io.codehunters:sdk-web` 1.0.0. Every
class was rewritten; none is source compatible.

#### Error responses

| commons-web | sdk-web-errors |
|---|---|
| `ApiError` / `ApiMessage` / `Api` (own JSON envelope with `code`, `message`, `timestamp`, `trace`, `errors`) | RFC 9457 `ProblemDetail` built by `WebErrorsExceptionHandler`: `code`, `detail`, `instance`, `traceId`, `errors` |
| `ApiError.trace` (the serialized `Throwable`) | Dropped. The exception is logged with the `traceId`; the client gets the `traceId` only |
| `timestamp` (`dd-MM-yyyy hh:mm:ss`, 12-hour, server time zone) | Dropped. Use the response `Date` header or the log, keyed by `traceId` |
| `ApiMessage.data` (success envelope) | Dropped. Return the resource itself |
| No global handler: every service wrote its own `@ControllerAdvice` | Auto-configured handler; services keep an advice only for exceptions they answer differently |

#### Exceptions

| commons-web | sdk-web-errors |
|---|---|
| `ContractException(message, code, HttpStatus, errors)` | `ApiException(ErrorCode, arguments)`; the code enum carries the status |
| `RestException(message, HttpStatus, errors)` (code fixed to `API-ERR`) | `ApiException` with a code of your own; validation errors are produced by the handler |
| The free-text `message` sent to the client | A `MessageSource` key per code (`error.<CODE>`), localized; the exception message is the code only |
| `errors` (`List<? extends Serializable>`, often `ObjectError`s with rejected values) | `errors` of `FieldViolation(field, code, message)`, never the rejected value |

#### Dropped, with their replacement

| commons-web | Replacement |
|---|---|
| `AbstractRestControllerSupport.createURI(path, id)` | `ServletUriComponentsBuilder.fromCurrentRequest().path(path).buildAndExpand(id).toUri()` |
| `AbstractRestControllerSupport.defaultBindingResultHasErrors` | `@Valid` on the parameter; the handler answers `VALIDATION_FAILED` |
| `PaginationRequest` / `PaginationResponse` | `Pageable` and a page DTO; a page response type is planned for `sdk-jpa` |
| `@UUIDConstraint` / `UUIDValidator` | Hibernate Validator's `@UUID`, or a `UUID` parameter type |
| `CollectionValidator` | Container element constraints: `List<@Valid Item>` |

### `commons-util-ip-support` and `commons-util-device-support`

`io.codehunters.commons:commons-util-ip-support` and `commons-util-device-support` 5.1.0
(`codehuntersio-java-commons-support`) are replaced by the device part of `io.codehunters:sdk-web` 1.0.0. Every class
was rewritten; none is source compatible.

| commons-support | sdk-device |
|---|---|
| `IpUtil.extractIp(request)` | `ClientContextResolver.resolve(request).ipAddress()`, or `ClientIpResolver.resolve(ProxyChain)` |
| `IpUtil.extractLocation(ip)`, `IpApiLocationHttpClient`, `IpLocation` | `GeoIpLookup` port, no implementation (see below) |
| `DeviceUtil.device(request[, useLocation])`, `Device` | `ClientContext` (IP, parsed and raw `User-Agent`); location through your own `GeoIpLookup` |
| `DeviceUtil.getUserAgent(request)` (`"family.major.minor\|os…\|device"` string) | `ClientContext.userAgent()` (`UserAgentInfo` record) |
| `DeviceUtil.getClient(request)` (`ua_parser.Client`, `null` on failure) | `UserAgentParser.parse(header)`, never null |
| `uap-java` 1.4.0, always on the classpath, a new `Parser` per call | Optional `uap-java` 1.6.1, one parser, bounded cache, header capped before parsing |

#### Intentionally not migrated

- **The leftmost `X-Forwarded-For` entry as the client IP.** `IpUtil` returned the first value of the
  header, which the client controls: any caller could set its own address, in audit trails and rate
  limits alike. `sdk-device` believes the header only as far as configured trusted proxies wrote it,
  and uses the remote address when none are configured.
- **The ip-api.com lookup.** `IpApiLocationHttpClient` sent every client address to a third party,
  over plain HTTP, on the request thread and without a timeout. Geolocation is a port now; back it
  with a local database.
- **`Device.serialId`, `additionalInfo` and the mutable builder.** `ClientContext` is an immutable
  record; data an application adds belongs in its own types.
