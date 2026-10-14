# Migrating sdk-user-security 5.x to 6.0

6.0.0 refuses to talk to Keycloak over plain `http` unless told to.

## Keycloak URLs must use https

`codehunters.security.url` is where the realm's signing keys are fetched (unless `jwk-set-uri` is
set), where session introspection sends `client-id:client-secret`, and where the policy enforcer of
`sdk-user-security-authz` sends its client secret. Over `http`, anyone on that network path can
replace the keys, and then forge any token, or read both secrets.

| `url` / `jwk-set-uri` | 5.x | 6.0 |
|---|---|---|
| `https://…` | accepted | accepted |
| `http://…` | accepted | startup fails, naming `allow-insecure-http` |

Either point the service at Keycloak's https endpoint, or, where cleartext is an accepted risk (a
local stack, a mesh that encrypts the hop), opt in explicitly:

```yaml
codehunters:
  security:
    url: http://keycloak:8080
    allow-insecure-http: true
```

`issuer-uri` is only compared with the tokens' `iss` claim and is never fetched, so it is not
checked.

## Constructing the properties by hand

`KeycloakSecurityProperties` has a new `allowInsecureHttp` component after `jwkSetUri`. Code that
calls its constructor directly (usually tests) adds the argument; binding from configuration needs
no change.
