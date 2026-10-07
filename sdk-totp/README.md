# sdk-totp

RFC 6238 time-based one-time passwords: generation, verification and shared-secret handling.
Artifact `io.codehunters:sdk-totp`, package `io.codehunters.totp`. Replaces `sdk-utils`
([ADR 0005](../docs/adr/0005-rename-sdk-utils-to-sdk-totp.md)); upgrading from 1.x is covered in
[MIGRATION-2.0.md](MIGRATION-2.0.md).

## Usage

```java
TotpSecret secret = TotpSecret.generate();          // 20 random bytes
String shared = secret.toBase32();                  // give this to the authenticator app

String code = totpGenerator.generate(secret);
boolean ok = totpGenerator.verify(secret, submitted, 1);
```

`TotpGenerator` works without Spring. With Spring Boot, the auto-configuration registers one bean
built from the properties below; declare your own `TotpGenerator` bean to replace it.

Secrets come from raw bytes (`fromBytes`), Base32 (`fromBase32`, case-insensitive, padding
optional) or hex (`fromHex`, as in the RFC test vectors). `toString()` never shows the key, and an
invalid Base32 or hex input fails with a `TotpException` that does not repeat the input.

## Configuration

| Property | Default | Notes |
|---|---|---|
| `codehunters.totp.time-step` | `30s` | Whole seconds, at least `1s`. Anything else fails startup. |
| `codehunters.totp.digits` | `six` | `four`, `six` or `eight`. |
| `codehunters.totp.algorithm` | `hmac-sha512` | `hmac-sha1`, `hmac-sha256` or `hmac-sha512`. |

The 1.x keys under `vitxo.totp.*` are still read, with a deprecation warning, until 3.0. A key set
under `codehunters.totp.*` wins over the old one.

Most authenticator apps only support HMAC-SHA1 with six digits and a 30-second step. Set
`codehunters.totp.algorithm=hmac-sha1` when codes are entered from such an app.

## Verification

- `variance` is the number of time steps accepted on each side of the current one, from `0` to
  `10` (`TotpGenerator.MAX_VARIANCE`). Other values throw `IllegalArgumentException`.
- Every candidate in the window is compared in constant time, and the comparison does not stop at
  the first match. A `null` code returns `false`.
- Verification does not stop a code from being used twice inside its window. If a code must be
  single-use, store the last accepted time step per secret and reject codes at or before it.
- Codes are always ASCII digits, whatever the JVM's default locale. Instants before the epoch use
  floor division, so they land in the correct window.

The implementation is checked against every test vector in RFC 6238, Appendix B.
