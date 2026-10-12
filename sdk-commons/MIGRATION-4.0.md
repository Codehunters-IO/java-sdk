# Migrating to sdk-commons 4.0

`io.codehunters:sdk-commons` 4.0.0 removes one class. Everything else (masking, text, passwords,
relative time, file names, streams, TOTP) is unchanged.

`ClientInputException` moved to `io.codehunters:sdk-errors` 1.0.0
([ADR 0028](../docs/adr/0028-error-contract-in-its-own-module.md)):

```diff
-import io.codehunters.commons.error.ClientInputException;
+import io.codehunters.errors.ClientInputException;
```

Add the dependency only if nothing brings it already: `sdk-web`, `sdk-jpa` and `sdk-barcode` do.

```kotlin
implementation("io.codehunters:sdk-errors:1.0.0")
```

The other way round: `sdk-jpa` 4.0.0 and `sdk-barcode` 3.0.0 no longer bring `sdk-commons`. If you
used its helpers only through one of them, declare it:

```kotlin
implementation("io.codehunters:sdk-commons:4.0.0")
```

Align the versions with the BOM (`io.codehunters:sdk-bom:6.0.0`): `sdk-web` 4.0.0 answers only the
new type with 400, so a subclass of the old one would fall through to 500.
