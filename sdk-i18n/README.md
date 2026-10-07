# sdk-i18n

Request locale resolution, a resource-bundle `MessageSource` and localized bean validation
messages for Spring Boot 4 applications.

```kotlin
implementation(platform("io.codehunters:sdk-bom:<version>"))
implementation("io.codehunters:sdk-i18n")
```

## What it registers

The auto-configuration has three independent parts. Each backs off when the application provides
its own bean.

| Part | Active when | Beans | Backs off on |
|---|---|---|---|
| Messages | always | `messageSource` (`ReloadableResourceBundleMessageSource`) | a bean named `messageSource` |
| Servlet web | servlet web application with Spring MVC | `localeResolver` (`XLocaleAwareLocaleResolver`), `localeMdcFilterRegistration` | a bean named `localeResolver` / `localeMdcFilterRegistration` |
| Validation | `jakarta.validation` and a provider (e.g. Hibernate Validator) on the classpath | `validatorFactoryBean` (`LocalValidatorFactoryBean`) | any `jakarta.validation.Validator` bean |

It runs before Boot's `MessageSourceAutoConfiguration`, `ValidationAutoConfiguration` and
`WebMvcAutoConfiguration`, so its beans win.

### Precedence over `spring.messages.*`

While this SDK provides `messageSource`, Boot's `spring.messages.*` settings are ignored, and a
warning is logged at startup if `spring.messages.basename` is set. Configure bundles under
`codehunters.i18n.*` instead. To use Boot's message source, declare your own `messageSource` bean
or set `codehunters.i18n.enabled=false`.

## Locale resolution

`XLocaleAwareLocaleResolver` picks the locale in this order:

1. The custom header (`X-Locale` by default). It takes BCP 47 tags (`en-US`) and Java's `en_US`
   form; blank, unparseable, or longer than 35 characters values are ignored.
2. `Accept-Language`.
3. `codehunters.i18n.default-locale`.

With `codehunters.i18n.supported-locales` set, both headers are matched against that list the
same way Spring's `AcceptHeaderLocaleResolver` does: an exact match wins, otherwise a supported
locale with the same language and no country (`en-GB` → `en`). A custom header that matches
nothing falls through to `Accept-Language`, and an `Accept-Language` that matches nothing falls
through to the default locale. Without the list, any locale is accepted.

## Locale in the MDC

`LocaleMdcFilter` puts the resolved language tag in the SLF4J MDC under `locale` for the duration
of the request, on async and error dispatches too, and restores the previous value afterwards. It
runs at `HIGHEST_PRECEDENCE + 10`, after the tracing filter. If the resolver throws, the filter
logs a warning with the exception class only (its message may echo request headers) and the
request continues without the MDC entry.

## Validation messages

The validator resolves `{key}` placeholders in constraint messages through the application's
message source, in the locale of `LocaleContextHolder` (set by Spring MVC from the resolver).
Keys that the message source does not have fall back to the provider's default messages.

```java
record CreateOrder(@NotNull(message = "{order.id.required}") String id) {}
```

```properties
# src/main/resources/i18n/messages_en.properties
order.id.required=The order id is required
```

## Configuration

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
[MIGRATION-1.0.md](MIGRATION-1.0.md).

## Known limits

- Bundles are cached for the life of the application; edits to bundle files need a restart.
- Only servlet applications get the resolver and the filter; WebFlux is not supported.
- Matching against `supported-locales` never maps a locale to one of another country
  (`es-MX` does not become `es-CO`), as in Spring's resolver.
