# sdk-totp

RFC 6238 time-based one-time passwords: generation, verification and shared-secret handling.
Artifact `io.codehunters:sdk-totp`, package `io.codehunters.totp`. Replaces `sdk-utils`
([ADR 0005](../docs/adr/0005-rename-sdk-utils-to-sdk-totp.md)); upgrading from 1.x is covered in
[MIGRATION-2.0.md](MIGRATION-2.0.md).

## Usage

```java
TotpSecret secret = TotpSecret.generate();          // 64 random bytes (HMAC-SHA512)
String shared = secret.toBase32();                  // give this to the authenticator app

String code = totpGenerator.generate(secret);
boolean ok = totpGenerator.verify(secret, submitted, 1);
```

`TotpGenerator` works without Spring. With Spring Boot, the auto-configuration registers one bean
built from the properties below; declare your own `TotpGenerator` bean to replace it. The
overloads without an `Instant` read the time from a `Clock`: the application's `Clock` bean when
there is exactly one, otherwise the UTC system clock. Outside Spring, pass one to
`new TotpGenerator(config, clock)`.

Secrets come from raw bytes (`fromBytes`), Base32 (`fromBase32`) or hex (`fromHex`, as in the RFC
test vectors). `toString()` never shows the key, and an invalid Base32 or hex input fails with a
`TotpException` that does not repeat the input.

- **Generated keys** are at least 16 bytes (`TotpSecret.MIN_KEY_LENGTH`, RFC 4226).
  `generate(TotpAlgorithm)` sizes the key to the hash: 20, 32 or 64 bytes. `generate()` uses the
  default HMAC-SHA512 and creates 64 bytes.
- **Imported keys** only have to be non-empty, so secrets enrolled earlier with a shorter key keep
  working.
- **Base32** is case-insensitive and accepts spaces between groups, as authenticator apps show
  them. Padding is optional, but only at the end. A length that no whole number of bytes encodes
  to, non-zero trailing bits and any other separator are rejected, so a mistyped secret fails at
  import instead of producing wrong codes.
- **Equality** compares keys in constant time. `hashCode()` is derived from the key; never log it.

## Configuration

| Property | Default | Notes |
|---|---|---|
| `codehunters.totp.time-step` | `30s` | Whole seconds, from `1s` to `10m`. Anything else fails startup. |
| `codehunters.totp.digits` | `six` | `six` or `eight`; `four` is deprecated (below RFC 4226). |
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
- **Rate-limit attempts.** The library has no throttling. Each guess is accepted with probability
  `(2 × variance + 1) / 10^digits`: one in about 476 with four digits and variance 10, one in about
  47,600 with six digits. Limit failed attempts per secret, as RFC 4226 §7.3 asks.
- Codes are always ASCII digits, whatever the JVM's default locale. Instants before the epoch use
  floor division, so they land in the correct window.

The implementation is checked against every test vector in RFC 6238, Appendix B.
