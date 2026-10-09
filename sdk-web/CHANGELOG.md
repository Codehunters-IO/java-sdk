# Changelog — sdk-web

## 3.0.0

2.1.0 was never released; its changes ship in 3.0.0.

### Changed
- Properties move under the artifact's prefix: `codehunters.web-errors.*` →
  `codehunters.web.errors.*`, `codehunters.device.*` → `codehunters.web.device.*`,
  `codehunters.i18n.*` → `codehunters.web.i18n.*`. Keys below the prefix keep their names, except the
  i18n message-source and default-locale keys, now Boot's (see Removed); the old prefixes are not
  read. See [MIGRATION-3.0.md](MIGRATION-3.0.md).
- The configuration metadata lists every renamed key with `level: error` and its replacement, so
  IDEs and `spring-boot-properties-migrator` report keys still using the old prefixes.
- i18n builds on Spring Boot instead of replacing it: the SDK no longer registers a
  `messageSource`, so Boot's applies and `spring.messages.*` is honoured (2.x ignored it with a
  warning). The errors part appends its bundle to the application's message source, after the
  application's bundles.
- `XLocaleAwareLocaleResolver` extends Spring's `AcceptHeaderLocaleResolver` semantics: its
  constructors are `(headerName)` and `(headerName, supportedLocales)`, the default locale comes
  from `setDefaultLocale` (`spring.web.locale`), and `Accept-Language` without `supported-locales`
  is the container's parse of it.

### Behaviour changes
- No SDK default locale: when no header resolves, the locale is `spring.web.locale`, else the
  container's (the JVM default without `Accept-Language`), as with Boot. 2.x answered `es`.
- Bundles follow Boot's defaults: base name `messages` at the class path root (2.x:
  `classpath:i18n/messages`), fallback to the JVM locale on (2.x: off).
- `XLocaleAwareLocaleResolver.setLocale` applies the locale to the rest of the request instead of
  throwing `UnsupportedOperationException`, so `LocaleChangeInterceptor` works.
- `spring.web.locale-resolver=fixed` makes the SDK's resolver back off for Boot's
  `FixedLocaleResolver`; 2.x ignored it with a warning.
- Spring Security 401 and 403 problem details resolve their locale with the application's
  `localeResolver`, so they follow `X-Locale`; before they used `Accept-Language` only.
- `LocaleMdcFilter` is registered only with `codehunters.web.i18n.mdc.enabled=true`.

### Removed
- The i18n `messageSource` and the properties that configured it: `codehunters.i18n.basename`,
  `encoding` and `fallback-to-system-locale` (use `spring.messages.*`), and `default-locale` (use
  `spring.web.locale`). The startup warnings about `spring.messages.*` and `spring.web.locale*`
  go with them.
- The pre-1.0 i18n property aliases, their `EnvironmentPostProcessor` and their deprecated
  configuration metadata.
- The dependency on `sdk-support`.

### Added
- `ClientInputException` from `sdk-commons` 3.0.0 is answered with `400` and its `code()`, also
  behind exceptions that merely wrap it: `InvalidDataAccessApiUsageException` (around `sdk-jpa`'s
  `InvalidSearchException`), `CompletionException`, `ExecutionException`,
  `UndeclaredThrowableException` and `ServletException`. Before, these answered `500
  INTERNAL_ERROR`. Any other wrapper keeps the `500`, and an `@ResponseStatus` exception keeps its
  status. The detail is the application's bundle message `error.<CODE>`, else the SDK's English or
  Spanish default for its own codes, else the exception message, safe by the exception's contract.
  A `ClientInputException` raised while Spring binds the request (Jackson, converters) still gets
  the framework's generic `400` without the code.
  Codes from the SDK: `INVALID_SORT`, `INVALID_SEARCH` (`sdk-jpa` 2.1.0), `BARCODE_IMAGE_REJECTED`,
  `BARCODE_CONTENT_REJECTED` (`sdk-barcode` 2.1.0). See the README's "Client input errors".
- `sdk-commons` is `compileOnly`: without it on the classpath the handler behaves as in 2.0.0.
- `codehunters.web.i18n.mdc.enabled` (default `false`).
- `ProblemWriter(MessageSource, TraceIdProvider, ProblemSettings, LocaleResolver)`.

## 2.0.0

### Breaking
- Packages move under the artifact's own: `io.codehunters.weberrors` → `io.codehunters.web.errors`,
  `io.codehunters.device` → `io.codehunters.web.device`, `io.codehunters.i18n` →
  `io.codehunters.web.i18n`. Sub-packages keep their names.
- `io.codehunters.weberrors.web` is split by concern: the MVC handler, error controller and writer
  go to `web.errors.servlet`, the Spring Security handlers to `web.errors.security`, and
  `TraceIdProvider` with `MdcTraceIdProvider` to `web.errors.trace`.
- Only imports change: class names, properties and behaviour are those of 1.0.0. See
  [MIGRATION-2.0.md](MIGRATION-2.0.md).

## 1.0.0

First release ([ADR 0016](../docs/adr/0016-consolidate-web-and-commons-artifacts.md)).

### Breaking
- One artifact, `io.codehunters:sdk-web`, replaces `sdk-web-errors` 1.0.0, `sdk-device` 1.0.0 and
  `sdk-i18n` 1.0.0. It declares their Gradle capabilities, so Gradle refuses a graph holding
  `sdk-web` and any of them; Maven builds must exclude them. See [MIGRATION-1.0.md](MIGRATION-1.0.md).

### Unchanged
- Packages (`io.codehunters.weberrors`, `io.codehunters.device`, `io.codehunters.i18n`), classes,
  properties and the behaviour of each part are those of the three
  1.0.0 releases below; what changes is that the parts now ship together (next section).
- Each part keeps its auto-configuration and its switch (`codehunters.web-errors.enabled`,
  `codehunters.device.enabled`, `codehunters.i18n.enabled`).

### Breaking for services that used only one part
- All three auto-configurations are on by default. A service that used only `sdk-web-errors` or
  `sdk-device` now gets the i18n `messageSource` (base name `classpath:i18n/messages`), which
  replaces Boot's and ignores `spring.messages.*`, and in servlet applications the `X-Locale`
  `localeResolver` and `localeMdcFilter`. Move the bundles or set `codehunters.i18n.enabled=false`;
  see [MIGRATION-1.0.md](MIGRATION-1.0.md).

## Before 1.0.0: `sdk-web-errors`

### sdk-web-errors 1.0.0

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
  [MIGRATION-1.0.md](MIGRATION-1.0.md#from-codehuntersio-java-commons-support).

## Before 1.0.0: `sdk-device`

### sdk-device 1.0.0

First release ([ADR 0012](../docs/adr/0012-trusted-proxy-client-ip.md)).

- `ClientIpResolver`: client address behind trusted proxies (`TrustedProxies`, CIDR, IPv4 and
  IPv6), reading only the configured `ForwardingHeader` (`X-Forwarded-For` by default, or RFC 7239
  `Forwarded`) right to left. No trusted proxies means the remote address. Unparseable hops stop the
  walk. Literals are parsed without DNS (`IpLiterals`).
- `UserAgentParser` port with `UapUserAgentParser` (optional `uap-java`), `UnknownUserAgentParser`
  and `CachingUserAgentParser` (bounded LRU, one parse per header in flight). `UserAgents.sanitize`
  replaces control characters and caps the length before parsing.
- `ClientContext` and, for servlet requests, `ClientContextResolver` (once per request; `current()`
  for the request bound to the thread).
- `GeoIpLookup` port and `GeoLocation`, without an implementation.
- `codehunters.device.*` properties, validated at startup.
- `ip`, `useragent` and `geo` depend on the JDK only; an ArchUnit test enforces it.
- Replaces `commons-util-ip-support` and `commons-util-device-support` from
  `codehuntersio-java-commons-support`; see
  [MIGRATION-1.0.md](MIGRATION-1.0.md#from-codehuntersio-java-commons-support).

Hardened before release, after review:

- A client-sent quote can no longer swallow the hop a proxy appends to its line: `X-Forwarded-For`
  is split on every comma, and a `Forwarded` line with unbalanced quotes is too.
- IPv6 literals must start with a hex digit or `:`, so no input reaches the system resolver.
- Trusted entries reject ports and `/0` blocks, and accept IPv4-mapped CIDRs (`::ffff:10.0.0.0/104`).
- The connection's link-local remote address drops its zone id instead of being lost.
- `max-length x cache-size` is capped at 33554432 characters (64 MB).
- An `Error` while parsing releases waiting requests with `UNKNOWN` and is not cached.
- Any `UserAgentParser`, including the application's, gives `UNKNOWN` instead of failing the
  request when it throws or returns `null`.
- `ClientContextResolver.current()` ignores a request that has already completed.
- README and ADR 0012: only `server.forward-headers-strategy=native` with `internal-proxies`
  replaces `trusted-proxies`; `framework` trusts the leftmost value.

## Before 1.0.0: `sdk-i18n`

### sdk-i18n 1.0.0

#### Breaking
- Properties moved under `codehunters.i18n.*`.
- `I18nProperties` is a record: `getBasename()` and the other getters become `basename()`,
  `defaultLocale()`, `headerName()`, `encoding()` and `fallbackToSystemLocale()`. `basename()`
  returns a `List<String>` and `encoding()` a `Charset`. There are no setters.
- Invalid settings fail at startup (empty or blank base name, a locale without a language, a header
  name that is not an HTTP token, an unknown charset, a default locale missing from a non-empty
  `supported-locales`).
- `localeResolver` and the MDC filter are only registered in servlet web applications (0.x
  registered them in any application with the servlet API on the classpath).
- The SDK's `messageSource` backs off only when a bean named `messageSource` exists (0.x backed off
  on any `MessageSource` bean, even one Spring would never use as the context's message source).
- No validator is registered: Boot's `defaultValidator` resolves `{key}` constraint messages through
  the SDK's message source, applies `ValidationConfigurationCustomizer` beans and keeps
  `ValidationMessages.properties`. 0.x registered `validatorFactoryBean`.
- Without `supported-locales`, an `Accept-Language` of `*`, with every range at weight zero, or
  unparseable resolves to `default-locale` instead of the container's root or JVM locale.
- Unicode extensions are dropped from `X-Locale`.
- `XLocaleAwareLocaleResolver.setLocale` throws `UnsupportedOperationException` with an explanation.
- `LocaleMdcFilter` now also runs on async and error dispatches.
- An `X-Locale` value longer than 35 characters is ignored.

#### Added
- `codehunters.i18n.enabled` to turn the auto-configuration off.
- `codehunters.i18n.supported-locales`, applied to both `X-Locale` and `Accept-Language`, and
  the `XLocaleAwareLocaleResolver(Locale, String, List<Locale>)` constructor.
- Several base names in `codehunters.i18n.basename`.
- `X-Locale` accepts Java's `en_US` form.
- `LocaleMdcFilter` logs resolver failures (exception class only) instead of swallowing them.
- A startup warning when `spring.messages.basename`, `spring.web.locale` or
  `spring.web.locale-resolver` is set, since the SDK's beans ignore them.
- Runs before Boot's message source and Web MVC auto-configurations.
