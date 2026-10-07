# Migrating sdk-i18n 0.x to 1.0

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
