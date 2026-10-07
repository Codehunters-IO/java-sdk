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
6. **Custom validators.** Any `jakarta.validation.Validator` bean replaces the SDK's validator.
   Without a Bean Validation provider (e.g. `hibernate-validator`) the SDK registers none.
7. **`spring.messages.*`.** Still ignored while the SDK provides the message source; startup now
   warns about it. Move the settings to `codehunters.i18n.*`.
8. **Restricting locales (optional).** Set `codehunters.i18n.supported-locales` to stop serving
   locales you have no bundles for; it must include the default locale.
