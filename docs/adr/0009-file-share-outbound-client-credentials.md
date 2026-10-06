---
status: accepted
date: 2026-10-06
decision-makers: [andres]
---

# 0009. Authenticate sdk-file-share calls with client credentials

## Context and Problem Statement

`sdk-file-share` 1.x called the file-share service without any credentials, so each service had to
wire authentication itself or leave the endpoint open. File operations also run where there is no
user request at all: schedulers, message listeners, outbox dispatch and `@Async` tasks. How should
the SDK authenticate its outbound calls?

## Considered Options

- Option A — Client credentials by default, through Spring Security's OAuth2 client, with a hook
  for application interceptors.
- Option B — Relay the bearer token of the current user request.
- Option C — Only a hook (`ClientHttpRequestInterceptor`); every application brings its own auth.
- Option D — Both A and B, selected by a `mode` property.

## Decision Outcome

Chosen option: **Option A**, because it works in every execution context (with or without a user
request), keeps token handling in Spring Security instead of the SDK, and still lets a service that
needs something else leave it off and set its own header.

- `codehunters.file-share.auth.client-registration-id` names a
  `spring.security.oauth2.client.registration.*` entry with the `client_credentials` grant. The SDK
  builds its own `AuthorizedClientServiceOAuth2AuthorizedClientManager`, which does not depend on a
  servlet request, and caches the token under a fixed principal
  (`codehunters.file-share.auth.principal`).
- `FileShareRequestInterceptor` beans run before the client-credentials step, which always sets
  the `Authorization` header when a registration id is configured. An application interceptor can
  add to authentication but never silently removes it; the only way to turn it off is to leave the
  registration id unset.
- A 401 or 403 from the service evicts the cached token. The token request uses the SDK's connect
  and read timeouts, and token-endpoint outages are reported as unavailability, not as a rejected
  client.
- Startup fails if the registration is missing, uses another grant type,
  `spring-security-oauth2-client` is not on the classpath, or the base URL is plain `http` without
  `codehunters.file-share.auth.allow-insecure-http=true`.

### Consequences

- **Good** — background jobs and listeners call file-share the same way as request threads.
- **Good** — token caching, refresh and clock skew come from Spring Security.
- **Bad** — the file-share service sees the calling service, not the end user; per-user
  authorization must be decided by the calling service (it passes the `referenceId` it owns).
- **Neutral** — `spring-security-oauth2-client` stays optional; without the property no token is
  sent, as in 1.x.

### Confirmation

- An end-to-end test against a local token endpoint proves the bearer token is sent, reused until it
  expires, and that a rejected client fails with `FileShareUnauthorizedException` without the token
  in the message.
- Context tests prove each startup failure, that an application interceptor runs alongside the
  token, that a 401 leads to a new token, and that a hung or unreachable token endpoint fails as
  `FileShareUnavailableException` within the configured timeout.

## More Information

- Migration plan: `docs/plans/2026-10-05-sdk-migration.md`, phase 5 (decision A4).
