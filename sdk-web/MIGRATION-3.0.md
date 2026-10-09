# Migrating to sdk-web 3.0

`io.codehunters:sdk-web` 3.0.0 moves the properties of its three parts under the artifact's own
prefix, `codehunters.web`, and builds the i18n part on Spring Boot's own message source and locale
resolver (see [i18n on Spring Boot](#i18n-on-spring-boot)). Packages and class names are those of
2.0.0, except `CachingUserAgentParser` (see [Device](#device)). The old prefixes are not read any
more: a service that keeps them starts with the defaults, silently.

## Properties

| 2.x | 3.0 |
|---|---|
| `codehunters.web-errors.*` | `codehunters.web.errors.*` |
| `codehunters.device.*` | `codehunters.web.device.*` |
| `codehunters.i18n.*` | `codehunters.web.i18n.*` |

Every key below the prefix keeps its name:

| 2.x | 3.0 |
|---|---|
| `codehunters.web-errors.enabled` | `codehunters.web.errors.enabled` |
| `codehunters.web-errors.type-base-uri` | `codehunters.web.errors.type-base-uri` |
| `codehunters.web-errors.include-trace-id` | `codehunters.web.errors.include-trace-id` |
| `codehunters.device.enabled` | `codehunters.web.device.enabled` |
| `codehunters.device.trusted-proxies` | `codehunters.web.device.trusted-proxies` |
| `codehunters.device.forwarding-header` | `codehunters.web.device.forwarding-header` |
| `codehunters.device.user-agent.max-length` | `codehunters.web.device.user-agent.max-length` |
| `codehunters.device.user-agent.cache-size` | `codehunters.web.device.user-agent.cache-size` |
| `codehunters.i18n.enabled` | `codehunters.web.i18n.enabled` |
| `codehunters.i18n.supported-locales` | `codehunters.web.i18n.supported-locales` |
| `codehunters.i18n.header-name` | `codehunters.web.i18n.header-name` |
| `codehunters.i18n.basename` | `spring.messages.basename` (Boot; see below) |
| `codehunters.i18n.default-locale` | `spring.web.locale` (Boot; see below) |
| `codehunters.i18n.encoding` | `spring.messages.encoding` (Boot) |
| `codehunters.i18n.fallback-to-system-locale` | `spring.messages.fallback-to-system-locale` (Boot) |
| — | `codehunters.web.i18n.mdc.enabled` (new, `false`) |

In YAML the three blocks move under `web`:

```yaml
# before
codehunters:
  web-errors:
    type-base-uri: https://errors.example.com/
  device:
    trusted-proxies: 10.0.0.0/8
  i18n:
    supported-locales: es,en
    default-locale: en

# after
codehunters:
  web:
    errors:
      type-base-uri: https://errors.example.com/
    device:
      trusted-proxies: 10.0.0.0/8
    i18n:
      supported-locales: es,en
spring:
  web:
    locale: en
```

Environment variables follow the same rule: `CODEHUNTERS_DEVICE_TRUSTEDPROXIES` becomes
`CODEHUNTERS_WEB_DEVICE_TRUSTEDPROXIES`, `CODEHUNTERS_I18N_HEADERNAME` becomes
`CODEHUNTERS_WEB_I18N_HEADERNAME`, `CODEHUNTERS_I18N_DEFAULTLOCALE` becomes `SPRING_WEB_LOCALE` and
`CODEHUNTERS_WEBERRORS_TYPEBASEURI` becomes `CODEHUNTERS_WEB_ERRORS_TYPEBASEURI`.

## i18n on Spring Boot

The i18n part no longer replaces Boot's beans; it adds the `X-Locale` header on top of them.

**Message source.** The SDK registers no `messageSource`: Boot's applies, configured by
`spring.messages.*`, which 2.x ignored. Its defaults differ from the SDK's old ones:

| | 2.x (`codehunters.i18n.*`) | 3.0 (`spring.messages.*`) |
|---|---|---|
| Base name | `classpath:i18n/messages` | `messages` (class path root) |
| Fallback to the JVM locale | `false` | `true` |
| Class | `ReloadableResourceBundleMessageSource` | `ResourceBundleMessageSource` |

Bundles kept under `src/main/resources/i18n/` need `spring.messages.basename=i18n/messages` (no
`classpath:` prefix; list several comma-separated) **and a base file**, `i18n/messages.properties`:
Boot registers its message source only when the first base name has one. Without it the SDK registers
a `messageSource` holding only its error bundle, so your own keys find nothing. Set
`spring.messages.fallback-to-system-locale=false` to keep 2.x's fallback to the base file. The SDK
appends its own error bundle after yours, and logs a `WARN` at startup when that bundle does not
resolve through the context (e.g. with a `messageSource` of your own that is not resource-based).

**Supported locales.** With `supported-locales` set, the resolved locale is always one of them: an
`Accept-Language` that matches none gives `spring.web.locale` if it is supported, else the first
supported locale. 2.x and Spring's resolver could answer an unsupported locale (the client's, or the
container's).

**Default locale.** There is no SDK default locale any more. 2.x answered `es` when no header
resolved; 3.0 answers `spring.web.locale`, else the servlet container's locale, which is the JVM
default when the request has no `Accept-Language`, as Boot does. Set `spring.web.locale=es` to keep
2.x's answer. `supported-locales` no longer has to include it.

**Resolver.** `XLocaleAwareLocaleResolver` extends Spring's `AcceptHeaderLocaleResolver`:
- Its constructors take `(headerName)` or `(headerName, supportedLocales)`; the default locale is set
  with `setDefaultLocale`.
- Without `supported-locales`, `Accept-Language` is the container's parse of it, as with Boot.
- `setLocale` no longer throws: the locale applies to the rest of the request, so a
  `LocaleChangeInterceptor` works.
- With `spring.web.locale-resolver=fixed` the SDK backs off and Boot's `FixedLocaleResolver` applies;
  2.x ignored the property.

**MDC filter.** `LocaleMdcFilter` is off by default. Set `codehunters.web.i18n.mdc.enabled=true` if
log lines rely on the `locale` MDC key. The key is not carried to `@Async` threads unless the
application propagates the MDC (Micrometer's `Slf4jThreadLocalAccessor` with a context-propagating
task decorator).

**Spring Security.** 401 and 403 problem details now follow `X-Locale`: `ProblemWriter` asks the
`localeResolver` bean instead of reading `Accept-Language`.

## Device

- **`CachingUserAgentParser`** moved to `io.codehunters.web.device.cache` and is backed by Caffeine:
  change the import if you build it yourself. Its constructor is the same. Eviction is no longer
  strict LRU but Caffeine's size-based policy; the bound is still `user-agent.cache-size`.
- **Servlet applications only.** `ClientIpResolver` and `UserAgentParser` are no longer registered
  in non-web applications. A batch job or consumer that injected them builds them instead:
  `new ClientIpResolver(TrustedProxies.of(...), ForwardingHeader.X_FORWARDED_FOR)`,
  `new CachingUserAgentParser(new UapUserAgentParser(), 1024)`.
- **One client address.** If `getRemoteAddr()`, access logs or Spring Security must show the same
  client as `ClientContextResolver`, use `server.forward-headers-strategy=native` with
  `server.tomcat.remoteip.internal-proxies` (and empty `trusted-proxies`), or turn on
  `codehunters.web.device.forwarded-request-filter.enabled`. See the README's "One client address
  for the whole request".

## Steps

1. **Dependency.** Bump `sdk-web` to 3.0.0, or the `sdk-bom` that manages it. `sdk-audit-log`
   reads the device part through `sdk-web`, so its client IP follows `codehunters.web.device.*` too.
2. **Configuration.** Rename the keys in every `application*.yml`/`.properties`, config server
   repository, Helm values and environment variable as in the tables above. For i18n, set
   `spring.messages.basename` (with its base file) and `spring.web.locale` as in
   [i18n on Spring Boot](#i18n-on-spring-boot).
3. **Check.** Startup fails on invalid values but not on unknown keys. Look for the old prefixes
   (`grep -rnE 'codehunters\.(web-errors|device|i18n)|CODEHUNTERS_(WEBERRORS|DEVICE|I18N)_'`) before
   deploying. The jar's configuration metadata lists every old key as an error with its
   replacement, so IDEs flag them in `application*.yml`, and adding
   `org.springframework.boot:spring-boot-properties-migrator` (runtime only, remove it afterwards)
   reports the old keys present in the environment at startup.
