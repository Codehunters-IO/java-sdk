# Changelog — sdk-i18n

## 1.0.0

### Breaking
- Properties moved from `vitxo.i18n.*` to `codehunters.i18n.*`. The old keys are still read
  until 2.0.0 and are marked deprecated in the configuration metadata.
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
- The validator is only registered when a Bean Validation provider is present, backs off on any
  `jakarta.validation.Validator` bean (0.x: only on a `LocalValidatorFactoryBean`), and resolves
  messages through the application context's message source rather than the SDK's bean directly.
- `LocaleMdcFilter` now also runs on async and error dispatches.
- An `X-Locale` value longer than 35 characters is ignored.

### Added
- `codehunters.i18n.enabled` to turn the auto-configuration off.
- `codehunters.i18n.supported-locales`, applied to both `X-Locale` and `Accept-Language`, and
  the `XLocaleAwareLocaleResolver(Locale, String, List<Locale>)` constructor.
- Several base names in `codehunters.i18n.basename`.
- `X-Locale` accepts Java's `en_US` form.
- `LocaleMdcFilter` logs resolver failures (exception class only) instead of swallowing them.
- A startup warning when `spring.messages.basename` is set, since the SDK's message source
  ignores it.
- Runs before Boot's message source, validation and Web MVC auto-configurations.
