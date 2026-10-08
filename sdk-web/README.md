# sdk-web

Servlet web toolkit for Spring MVC services, in three independent parts:

| Part | Package | Properties | What it does |
|---|---|---|---|
| [Errors](#errors--iocodehuntersweberrors) | `io.codehunters.weberrors` | `codehunters.web-errors.*` | RFC 9457 problem details with stable codes |
| [Device](#device--iocodehuntersdevice) | `io.codehunters.device` | `codehunters.device.*` | Client IP behind trusted proxies, User-Agent, geo-IP port |
| [i18n](#i18n--iocodehuntersi18n) | `io.codehunters.i18n` | `codehunters.i18n.*` | `X-Locale` resolution, `MessageSource`, localized validation |

Each part has its own auto-configuration and switch: set `codehunters.web-errors.enabled`,
`codehunters.device.enabled` or `codehunters.i18n.enabled` to `false` to drop it. Until 1.0.0 the
parts were three artifacts, `sdk-web-errors`, `sdk-device` and `sdk-i18n`
([ADR 0016](../docs/adr/0016-consolidate-web-and-commons-artifacts.md)); packages and properties did
not change, and [MIGRATION-1.0.md](MIGRATION-1.0.md) covers the switch.

```kotlin
implementation(platform("io.codehunters:sdk-bom:<version>"))
implementation("io.codehunters:sdk-web")
// Optional: real User-Agent parsing. Without it every client is "Other".
implementation("com.github.ua-parser:uap-java:1.6.1")
```

Web dependencies (Spring MVC, the servlet API, Spring Security, Jackson, `sdk-tracing`) are
`compileOnly`: the parts that need them activate only when the application has them.

## Errors — `io.codehunters.weberrors`

One error format for every Spring MVC service: [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)
problem details with a stable error `code`, a localized `detail`, the `traceId` and, for validation
failures, the list of violated constraints. Rejected values, exception messages and stack traces
never reach the client.

It covers exceptions handled by Spring MVC, the servlet container's error dispatch, and — once wired
into the filter chain — Spring Security's 401 and 403 (see
[Errors outside Spring MVC](#errors-outside-spring-mvc)).

### Response shape

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

### Throwing errors

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

### What is mapped

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

### Messages

Details are resolved through the application's `MessageSource` (the [i18n](#i18n--iocodehuntersi18n) bundles
when that part is enabled), in the request locale:

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

### Errors outside Spring MVC

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

### Logging

- 5xx: `ERROR` with the status, code, trace id and the exception, stack trace included. This is the
  only place the exception message appears. The exception is also exposed to the observation
  filter, so `http.server.requests` and the server span record it.
- Async request timeouts (503): `WARN` without the stack trace.
- 4xx: `DEBUG` with the status, code and trace id, never the arguments or rejected values. When an
  `ApiException` has a cause, the exception and its cause are logged at `DEBUG`.
- Log lines carry the trace id even with `include-trace-id=false`.

### Configuration

| Property | Default | Meaning |
|---|---|---|
| `codehunters.web-errors.enabled` | `true` | Turns the SDK off |
| `codehunters.web-errors.type-base-uri` | — | Absolute base URI ending with `/` for `type`; startup fails otherwise |
| `codehunters.web-errors.include-trace-id` | `true` | Adds the `traceId` member |

### Precedence and back-off

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

### Known limits

- Servlet stack only; WebFlux is not supported.
- `field` of a method parameter violation is the Java parameter name, which needs `-parameters`
  (Spring Boot's Gradle and Maven plugins enable it); without it the field is `argN`.
- `ApiException` cannot be serialized: its code and arguments are not required to be.

## Device — `io.codehunters.device`

Who is calling a service: the client IP as resolved behind **trusted** proxies, the parsed
`User-Agent`, and a port for geo-IP lookups that keeps addresses inside the service.

### Client IP

`X-Forwarded-For` and `Forwarded` are headers the client can send too. The SDK only believes them as
far as your own proxies wrote them ([ADR 0012](../docs/adr/0012-trusted-proxy-client-ip.md)):

```yaml
codehunters:
  device:
    trusted-proxies: 10.0.0.0/8, 2001:db8::/32   # your load balancers / ingress
    forwarding-header: x-forwarded-for            # or "forwarded" (RFC 7239)
```

- **No trusted proxies (the default):** the connection's remote address is the client. Forwarding
  headers are ignored, so nothing can be spoofed.
- **Trusted proxies:** starting from the remote address, hops are read right to left while the
  current address is a trusted proxy; the first address that is not trusted is the client. If every
  hop is trusted, the leftmost one is returned.
- **Only the configured header is read.** Proxies that append `X-Forwarded-For` pass a client-sent
  `Forwarded` through untouched (and vice versa), so reading both would let the client choose.
- **Unparseable hops stop the walk.** `unknown`, obfuscated identifiers (`_hidden`) and garbage are
  never returned; the last address with a known origin is, because anything to its left may have
  been written by the client.
- **Client text cannot swallow proxy hops.** Proxies append to the line the client sent (`"`,
  then `, 203.0.113.9`). `X-Forwarded-For` has no quoted strings and is split on every comma; a
  `Forwarded` line whose quotes do not balance is split on every comma too, so only the client's own
  elements become invalid.
- **No DNS.** Only IP literals are parsed (IPv4, IPv6 with or without brackets, optional port); an
  IPv6 literal must start with a hex digit or `:`. Host names and zone ids in headers are rejected.
  The connection's own link-local address keeps its address and drops its zone id
  (`fe80::1%en0` becomes `fe80:0:0:0:0:0:0:1`).
- **Trusted entries are strict:** no ports, and no `0.0.0.0/0` or `::/0` (trusting every hop
  would return the client-written leftmost one). IPv4-mapped blocks are accepted
  (`::ffff:10.0.0.0/104` is `10.0.0.0/8`).
- IPv6 addresses are returned in the JDK's full form (`2001:db8:0:0:0:0:0:1`).

#### Container forward headers

`server.forward-headers-strategy` rewrites the remote address before the SDK sees it:

- **`native`** (Tomcat's `RemoteIpValve`) is equivalent to `trusted-proxies` only when
  `server.tomcat.remoteip.internal-proxies` lists your proxies. Then leave `trusted-proxies` empty:
  the remote address is already the resolved client.
- **`framework`** (Spring's `ForwardedHeaderFilter`) takes the **leftmost** `Forwarded` or
  `X-Forwarded-For` value without any trust check, so any client can choose its address. Turn it
  off and configure `trusted-proxies` instead, or accept that the address can be spoofed.

The resolution itself is plain Java (`io.codehunters.device.ip`), usable outside Spring:

```java
var resolver = new ClientIpResolver(
    TrustedProxies.of(List.of("10.0.0.0/8")), ForwardingHeader.X_FORWARDED_FOR);
Optional<InetAddress> client =
    resolver.resolve(new ProxyChain(remoteAddr, forwardedLines, xForwardedForLines));
```

### User-Agent

- The header is sanitized first: control characters become spaces and it is cut to
  `user-agent.max-length` (512) characters, so a crafted header cannot make the regex-based parser
  slow and never ends in half a surrogate pair.
- With `uap-java` on the classpath, `UapUserAgentParser` reads browser, OS and device families and
  versions; parses are kept in a bounded LRU cache (`user-agent.cache-size`, 1024; 0 disables it),
  and concurrent requests with the same header share one parse. `max-length x cache-size` may not
  exceed 33554432 characters (64 MB). The cache saves repeated parses only: clients sending a
  different header each time still cost one parse of up to `max-length` characters per request.
- Without it, `UnknownUserAgentParser` answers `UserAgentInfo.UNKNOWN` (families `Other`, empty
  versions). The sanitized raw header is still available.
- A parser exception, or a `null` answer from any parser including your own, gives `UNKNOWN`; it
  never fails the request. An `Error` thrown while parsing (a `StackOverflowError` in the regex
  engine, say) releases the requests waiting on that parse with `UNKNOWN` and is not cached.
- Define your own `UserAgentParser` bean to use another library.

`uap-java` 1.6.1 (December 2023) is the latest release and has no published advisories; the ReDoS
issues reported against `ua-parser` regexes were fixed in the bundled `uap-core` before it. The
length cap is the defence in depth.

### Request context

In servlet applications `ClientContextResolver` combines both, once per request (kept as a request
attribute):

```java
ClientContext client = clientContextResolver.resolve(request);   // or .current()
client.ipAddress();      // Optional<String>
client.userAgent();      // UserAgentInfo, never null
client.rawUserAgent();   // sanitized, truncated; "" when absent
```

`sdk-audit-log` uses it when both are present: its default `AuditContextProvider` then fills the
IP and `User-Agent` of audit events.

### Geo-IP

`GeoIpLookup` is a port only; the SDK ships no implementation. Back it with a local database such as
MaxMind GeoLite2/GeoIP2. Sending client addresses to a third-party HTTP API (as the old
`commons-util-ip-support` did with ip-api.com, over plain HTTP) discloses personal data and is not
supported.

### Configuration

| Property | Default | Notes |
|---|---|---|
| `codehunters.device.enabled` | `true` | |
| `codehunters.device.trusted-proxies` | empty | Addresses or CIDR blocks; an invalid entry, a port or a `/0` block fails startup |
| `codehunters.device.forwarding-header` | `x-forwarded-for` | Or `forwarded` |
| `codehunters.device.user-agent.max-length` | `512` | 16 to 8192 |
| `codehunters.device.user-agent.cache-size` | `1024` | 0 to 100000; 0 disables the cache; `max-length x cache-size` at most 33554432 |

Every bean (`ClientIpResolver`, `UserAgentParser`, `ClientContextResolver`) backs off when the
application defines its own. `ClientContextResolver` is registered in servlet applications only.

### Known limits

- WebFlux is not supported: there is no reactive `ClientContextResolver`. The plain-Java
  `ClientIpResolver` can be called with a `ProxyChain` built from a `ServerHttpRequest`.
- `RFC 7239` `by`, `host` and `proto` parameters are not used.

## i18n — `io.codehunters.i18n`

Request locale resolution, a resource-bundle `MessageSource` and localized bean validation
messages for Spring Boot 4 applications.

### What it registers

The auto-configuration has two independent parts. Each backs off when the application provides
its own bean.

| Part | Active when | Beans | Backs off on |
|---|---|---|---|
| Messages | always | `messageSource` (`ReloadableResourceBundleMessageSource`) | a bean named `messageSource` |
| Servlet web | servlet web application with Spring MVC | `localeResolver` (`XLocaleAwareLocaleResolver`), `localeMdcFilterRegistration` | a bean named `localeResolver` / `localeMdcFilterRegistration` |

It runs before Boot's `MessageSourceAutoConfiguration` and `WebMvcAutoConfiguration`, so its beans
win. It registers no validator: Boot's `defaultValidator` already resolves `{key}` messages through
the `messageSource` (see [Validation messages](#validation-messages)).

#### Precedence over `spring.messages.*`

While this SDK provides `messageSource`, Boot's `spring.messages.*` settings are ignored, and a
warning is logged at startup if `spring.messages.basename` is set. Configure bundles under
`codehunters.i18n.*` instead. To use Boot's message source, declare your own `messageSource` bean
or set `codehunters.i18n.enabled=false`.

#### Precedence over `spring.web.locale*`

While this SDK provides `localeResolver`, Boot's `spring.web.locale` and
`spring.web.locale-resolver` have no effect; startup warns when either is set. Use
`codehunters.i18n.default-locale` instead, or declare your own `localeResolver` bean.

### Locale resolution

`XLocaleAwareLocaleResolver` picks the locale in this order:

1. The custom header (`X-Locale` by default). It takes BCP 47 tags (`en-US`) and Java's `en_US`
   form; blank, unparseable, or longer than 35 characters values are ignored.
2. `Accept-Language`. Without `supported-locales`, the highest-weighted range that names a
   language; `*` and ranges of weight zero are skipped.
3. `codehunters.i18n.default-locale`, also when `Accept-Language` is unparseable or names no usable
   language (never the container's root locale or the JVM locale).

Unicode extensions (`en-US-u-ca-japanese`) are dropped from the custom header, so a client cannot
switch calendars or numbering systems.

With `codehunters.i18n.supported-locales` set, both headers are matched against that list the
same way Spring's `AcceptHeaderLocaleResolver` does: an exact match wins, otherwise a supported
locale with the same language and no country (`en-GB` → `en`). A custom header that matches
nothing falls through to `Accept-Language`, and an `Accept-Language` that matches nothing falls
through to the default locale. Without the list, any locale is accepted.

### Locale in the MDC

`LocaleMdcFilter` puts the resolved language tag in the SLF4J MDC under `locale` for the duration
of the request, on async and error dispatches too, and restores the previous value afterwards. It
runs at `HIGHEST_PRECEDENCE + 10`, after the tracing filter. If the resolver throws, the filter
logs a warning with the exception class only (its message may echo request headers) and the
request continues without the MDC entry.

### Validation messages

Boot's validator (`ValidationAutoConfiguration`, from `spring-boot-starter-validation`) resolves
`{key}` placeholders in constraint messages through the application's message source, in the locale
of `LocaleContextHolder` (set by Spring MVC from the resolver). Keys the message source does not
have fall back to `ValidationMessages.properties` and then to the provider's default messages.
`ValidationConfigurationCustomizer` beans apply as usual.

```java
record CreateOrder(@NotNull(message = "{order.id.required}") String id) {}
```

```properties
# src/main/resources/i18n/messages_en.properties
order.id.required=The order id is required
```

### Configuration

| Property | Default | Meaning |
|---|---|---|
| `codehunters.i18n.enabled` | `true` | Turn the whole auto-configuration off |
| `codehunters.i18n.basename` | `classpath:i18n/messages` | Bundle base names, comma-separated, in lookup order |
| `codehunters.i18n.default-locale` | `es` | Locale used when no header resolves, and the bundle fallback |
| `codehunters.i18n.supported-locales` | empty (any) | Locales the application serves (`es,en-US`); must include the default locale |
| `codehunters.i18n.header-name` | `X-Locale` | Header that overrides `Accept-Language` |
| `codehunters.i18n.encoding` | `UTF-8` | Bundle encoding |
| `codehunters.i18n.fallback-to-system-locale` | `false` | Fall back to the JVM locale when a bundle is missing |

Invalid values fail at startup: an empty or blank base name, a locale without a language, a
default locale missing from a non-empty `supported-locales`, a header name that is not an HTTP
token, or an unknown charset.

The 0.x `vitxo.i18n.*` keys keep working until 2.0.0 under their new names; a key set under
`codehunters.i18n.*` wins, and startup logs which old keys were found. See
[MIGRATION-1.0.md](MIGRATION-1.0.md#from-sdk-i18n-0x).

### Known limits

- Bundles are cached for the life of the application; edits to bundle files need a restart.
- Only servlet applications get the resolver and the filter; WebFlux is not supported.
- Matching against `supported-locales` never maps a locale to one of another country
  (`es-MX` does not become `es-CO`), as in Spring's resolver. Scripts are kept, so `zh-Hant-TW`
  does not match a supported `zh-TW`.
- The locale comes from request headers only: `LocaleResolver.setLocale` (and so
  `LocaleChangeInterceptor`) throws `UnsupportedOperationException`.
