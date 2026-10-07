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
- No validator is registered: Boot's `defaultValidator` resolves `{key}` constraint messages through
  the SDK's message source, applies `ValidationConfigurationCustomizer` beans and keeps
  `ValidationMessages.properties`. 0.x registered `validatorFactoryBean`.
- Without `supported-locales`, an `Accept-Language` of `*`, with every range at weight zero, or
  unparseable resolves to `default-locale` instead of the container's root or JVM locale.
- Unicode extensions are dropped from `X-Locale`.
- `XLocaleAwareLocaleResolver.setLocale` throws `UnsupportedOperationException` with an explanation.
- `LocaleMdcFilter` now also runs on async and error dispatches.
- An `X-Locale` value longer than 35 characters is ignored.

### Added
- `codehunters.i18n.enabled` to turn the auto-configuration off.
- `codehunters.i18n.supported-locales`, applied to both `X-Locale` and `Accept-Language`, and
  the `XLocaleAwareLocaleResolver(Locale, String, List<Locale>)` constructor.
- Several base names in `codehunters.i18n.basename`.
- `X-Locale` accepts Java's `en_US` form.
- `LocaleMdcFilter` logs resolver failures (exception class only) instead of swallowing them.
- A startup warning when `spring.messages.basename`, `spring.web.locale` or
  `spring.web.locale-resolver` is set, since the SDK's beans ignore them.
- Legacy keys set as environment variables may spell dashed names with underscores
  (`VITXO_I18N_DEFAULT_LOCALE` as well as `VITXO_I18N_DEFAULTLOCALE`); startup also lists old keys
  ignored because the new key is set.
- Runs before Boot's message source and Web MVC auto-configurations.
