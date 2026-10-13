# Migrating to sdk-web 4.0

`io.codehunters:sdk-web` 4.0.0 answers `ClientInputException` from its new home, `sdk-errors`
([ADR 0028](../docs/adr/0028-error-contract-in-its-own-module.md)). Responses, codes and bundle keys
do not change.

1. Code that throws or catches the exception changes one import:

   ```diff
   -import io.codehunters.commons.error.ClientInputException;
   +import io.codehunters.errors.ClientInputException;
   ```

   `sdk-web` brings `sdk-errors`; nothing to add.
2. Move the other SDKs along (`sdk-bom` 6.0.0 aligns them): `sdk-jpa` 4.0.0, `sdk-barcode` 3.0.0 and
   `sdk-commons` 4.0.0. An exception of the old `sdk-commons` 3.x type is no longer answered with
   400; it falls through to 500.
3. The mapping is now unconditional. Before, it was active only when `sdk-commons` was on the class
   path.
