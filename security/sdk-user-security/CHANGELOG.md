# Changelog — sdk-user-security

## 5.0.0

Unreleased 4.1.0 became 5.0.0: removing the aliases below is breaking. See
[MIGRATION-5.0.md](MIGRATION-5.0.md).

### Removed
- The `keycloak.security.*` aliases. Only `codehunters.security.*` is read; an old key is ignored.
- `KeycloakSecurityLegacyProperties`, its `spring.factories` entry and the deprecated
  `keycloak.security.*` configuration metadata.
- The dependency on `sdk-support`.

### Added
- `io.codehunters.security.KeycloakAuthenticationToken`, the authentication of a verified Keycloak
  token: an `AbstractOAuth2TokenAuthenticationToken<Jwt>` whose principal is the `KeycloakPrincipal`.

### Behaviour changes
- The authentication of a bearer request is a `KeycloakAuthenticationToken` instead of a
  `UsernamePasswordAuthenticationToken`. The validated `Jwt` stays available through `getToken()`
  after Spring Security erases credentials, and the gateway filter keeps the type when it adds the
  gateway context. `getPrincipal()` is still the `KeycloakPrincipal`, so `@CurrentPrincipal`,
  `@AuthenticationPrincipal KeycloakPrincipal` and `KeycloakSecurityUtils` work unchanged; code that
  cast the authentication to `UsernamePasswordAuthenticationToken` must cast to
  `KeycloakAuthenticationToken` or `AbstractOAuth2TokenAuthenticationToken`.
- `Authentication.getName()` and `HttpServletRequest.getRemoteUser()` return the user id instead of
  the record's `toString()`; `authentication.name` in `@PreAuthorize` changes accordingly.
- `KeycloakPrincipal.toString()` prints the user id only.
- A `KeycloakPrincipal` with a null or blank user id is refused, and a token without `sub` is
  rejected as an invalid bearer token (401).
- The gateway-header 401 body and headers change (see Fixed).
- The JSON form of `KeycloakPrincipal` is unchanged from 4.0.0.

### Fixed
- `Authentication.getName()`, `HttpServletRequest.getRemoteUser()` and `authentication.name` in
  `@PreAuthorize` are the user id. `KeycloakPrincipal` now implements `AuthenticatedPrincipal`;
  before, Spring Security fell back to the record's `toString()`, so the name carried the gateway
  IP, geolocation and User-Agent into security events and access logs, and
  `@PreAuthorize("authentication.name == #id")` never matched.
- `KeycloakPrincipal.toString()` prints the user id only.
- Serialized with Jackson, `KeycloakPrincipal` writes the same fields as 4.0.0: `getName()` is
  `@JsonIgnore`, so stored principals read back with unknown properties failing.
  `gatewayContext()` is not a bean property, so it adds nothing to the JSON. The JSON still carries
  the unverified gateway values; the redaction covers `toString()` only.
- With several `AuthenticationEntryPoint` or `AccessDeniedHandler` beans and no `@Primary`, Spring's
  defaults answer the Keycloak chain; a startup warning now says so.
- The 401 for gateway headers that disagree with the token goes through the application's
  `AuthenticationEntryPoint`, so with `sdk-web` it carries `code`, `instance` and `traceId` like the
  other 401s. Without an entry point it is a problem detail with `code: UNAUTHORIZED` and
  `instance`, plus the bearer `WWW-Authenticate` challenge.

### Added
- `GatewayContext`: the gateway's `x-geo-*` and `User-Agent` values, documented as client-supplied
  and not verified. Read it with `KeycloakSecurityUtils.getGatewayContext()` or
  `KeycloakPrincipal.gatewayContext()`; its `toString()` prints no value.
- `GatewayHeadersFilter(AuthenticationEntryPoint)` for applications that wire the filter into their
  own chain.

### Deprecated
- `KeycloakSecurityUtils.getCurrentIp()`, `getCurrentCountry()`, `getCurrentCity()`,
  `getCurrentLatitude()`, `getCurrentLongitude()` and the `KeycloakPrincipal` accessors `ip()`,
  `country()`, `city()`, `latitude()`, `longitude()`, `userAgent()`, for removal. Use
  `getGatewayContext()`, or `sdk-web`'s `ClientContextResolver` for a trusted client IP. See the
  README's trust model.

## 4.0.0

### Breaking
- `io.codehunters.security` keeps what applications use: `CurrentPrincipal`, `KeycloakPrincipal`,
  `KeycloakSecurityUtils` and `Authorities` (now public). The rest moves by concern:
  `security.config` (auto-configuration and properties), `security.jwt` (token converter),
  `security.session` (introspection), `security.gateway` (gateway headers filter) and
  `security.mvc` (`@CurrentPrincipal` argument resolver). Only imports change; see
  [MIGRATION-4.0.md](MIGRATION-4.0.md).

### Changed
- The `keycloak.security.*` aliases are honoured until 5.0.0 (was 4.0.0).
- Problem-detail bodies come from `sdk-web` 2.0 (`io.codehunters.web.errors`).

## 3.1.0

### Added
- 401 and 403 go through the application's single `AuthenticationEntryPoint` and
  `AccessDeniedHandler` beans when present. With `sdk-web-errors` that means problem-detail bodies
  that keep the bearer challenge. Without such beans nothing changes.

### Fixed
- Ordered before Spring Boot 4.1's resource-server auto-configurations
  (`OAuth2ResourceServerAutoConfiguration` and `OAuth2ResourceServerWebSecurityAutoConfiguration`
  moved packages); the old class name no longer exists.

## 3.0.0

### Breaking
- Identity and authorities come from the bearer token, verified locally against the realm's JWK set
  (signature, issuer, expiry, subject, optional audience). Gateway `x-user-id` / `x-user-roles`
  headers must agree with the token or the request is rejected with 401.
- Package `io.codehunters.sdk.security` → `io.codehunters.security`.
- Properties `keycloak.security.*` → `codehunters.security.*`; introspection settings move under
  `codehunters.security.introspection`. Old keys are honoured until 4.0 with a warning.
- Old keys are read the way Boot binds them, so an environment variable may spell a dashed name
  with or without underscores (`KEYCLOAK_SECURITY_CLIENT_SECRET` or `KEYCLOAK_SECURITY_CLIENTSECRET`). Only the documented old keys are aliased, and
  startup lists old keys ignored because the new key is set.
- `fail-open` defaults to `false`.
- No longer gated by the `develop`/`cert`/`prod` profiles; use `codehunters.security.enabled`.
- `codehunters.security.audiences` is required (or `allow-any-audience=true`).
- `public-paths` / `excluded-paths` use `PathPattern` syntax; `**` in the middle of a pattern fails
  startup.
- The Keycloak policy enforcer moved to `sdk-user-security-authz`.

### Fixed
- Forged `x-user-roles` could grant any role when the caller bypassed the gateway.
- Missing `url`/`realm` made every introspection fail and, with fail-open, allowed every request;
  required settings are now validated at startup.
- The introspection client had no timeouts and its cache could grow without bound; raw tokens were
  cache keys. Calls now have connect/read timeouts, the cache is a size-capped LRU keyed by SHA-256,
  and failures are never cached.
- Role names were upper-cased with the default locale (`admin` → `ROLE_ADMİN` under Turkish).
- Malformed `x-user-roles` JSON fell back to CSV parsing and produced junk roles; it is rejected.

### Added
- `issuer-uri` and `jwk-set-uri` overrides; `method-security.enabled`.
- The key set is cached and rate-limited; unknown key ids answer 401 without refetching it.

### Removed
- Dependencies on AspectJ, Micrometer, `spring-aop` and `spring-expression`, which were unused.
