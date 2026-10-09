# Migrating to sdk-commons 3.0

`io.codehunters:sdk-commons` 3.0.0 changes two TOTP defaults. Packages, class names and the
`io.codehunters.commons.*` helpers do not change. 2.x code compiles unchanged; what changes is
behaviour when you relied on a default.

| | 2.x | 3.0 |
|---|---|---|
| `TotpConfig.defaultConfig()`, `new TotpGenerator()` | HMAC-SHA512, 6 digits, 30 s | **HMAC-SHA1**, 6 digits, 30 s |
| `codehunters.totp.algorithm` when unset | `hmac-sha512` | **`hmac-sha1`** |
| `TotpSecret.generate()` | 64 bytes | **20 bytes** |
| `codehunters.totp.enabled` when unset | `true` | **`false`** |

Why: Google Authenticator, Microsoft Authenticator and most other apps only compute HMAC-SHA1 codes
and ignore the `algorithm` of a key URI, so users enrolled with the 2.x defaults got codes the
server rejected. And the auto-configuration registered a `TotpGenerator` in every Boot application
that used any sdk-commons helper.

## 1. Keep the algorithm your secrets were enrolled with

A secret is tied to the algorithm it was enrolled with: switching it changes every code. If you
used the 2.x default, **existing secrets generated with SHA-512 must keep that algorithm**.

With Spring Boot, set it explicitly to keep 2.x behaviour:

```properties
codehunters.totp.algorithm=hmac-sha512
```

(`HMAC_SHA512` binds too.) Without Spring, pass it in the config:

```java
new TotpGenerator(new TotpConfig(Duration.ofSeconds(30), TotpDigits.SIX, TotpAlgorithm.HMAC_SHA512));
```

If those users actually enrol in an authenticator app, their codes never matched; re-enrol them
with the new default (`TotpSecret.generate()` and `provisioningUri`) and store the algorithm per
secret if both must coexist, with one `TotpGenerator` per algorithm.

Code that calls `TotpSecret.generate()` and relies on a 64-byte key calls
`TotpSecret.generate(TotpAlgorithm.HMAC_SHA512)` instead.

## 2. Turn the auto-configuration on

Applications that inject the auto-configured `TotpGenerator` add:

```properties
codehunters.totp.enabled=true
```

Without it the bean is missing and startup fails with an `UnsatisfiedDependencyException` caused by
a `NoSuchBeanDefinitionException`. Applications
that declare their own `TotpGenerator` bean, or only use the helpers, need nothing. Remove any
`codehunters.totp.enabled=false` you set only to keep TOTP out: it is now the default.

## New in 3.0

- `verifyAfter(secret, code, variance, lastAcceptedStep[, at])` rejects a code whose time step is
  not after the one you stored, and returns the matched step to store next. `verifyStep` returns the
  step without that check. See *One-time use* in the [README](README.md#one-time-use-reject-replayed-codes).
- `provisioningUri(secret, issuer, account)` builds the `otpauth://totp/` key URI for the QR code.

## Removed in 3.0

- The pre-2.0 TOTP property aliases are no longer read: only `codehunters.totp.*` binds. A service
  still configured with the old keys starts with the defaults above, silently; rename them to
  `codehunters.totp.time-step`, `codehunters.totp.digits` and `codehunters.totp.algorithm`.
- `sdk-commons` no longer brings `io.codehunters:sdk-support`. Nothing in the public API used it;
  code that imported its internal classes declares it itself.
