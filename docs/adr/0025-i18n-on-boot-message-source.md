# 0025 — sdk-web i18n on Spring Boot's message source

- Status: accepted
- Date: 2026-10-09
- Amends: [0016](0016-consolidate-web-and-commons-artifacts.md) (the i18n part of sdk-web)

## Context

The i18n part of `sdk-web` registered its own `messageSource` and locale resolver. That silently
replaced Boot's `MessageSourceAutoConfiguration` (so `spring.messages.*` was ignored), forced `es` as
the default locale, threw from `LocaleResolver.setLocale` (breaking `LocaleChangeInterceptor`), and
left 401/403 problem details in the `Accept-Language` locale because the security chain did not use
the SDK resolver.

## Decision

- Boot owns the message source (`spring.messages.*`). The SDK only appends its own error bundle after
  the application's bundles, so application keys win.
- The `X-Locale` resolver extends `AcceptHeaderLocaleResolver`: `setLocale`, then `X-Locale`, then
  `Accept-Language`, `spring.web.locale` and the container locale. There is no SDK default locale; it
  backs off when `spring.web.locale-resolver=fixed`.
- 401/403 problem details resolve their locale through the application's `LocaleResolver`.
- The locale MDC filter is opt-in.

## Consequences

- Good: Boot's configuration works as documented; the SDK adds only `X-Locale` and its error texts.
- Bad: services that relied on the forced `es` must set `spring.web.locale=es`; Boot needs a base
  bundle (`messages.properties`) before it registers a message source.
