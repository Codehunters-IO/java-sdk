# Migrating to sdk-jpa 4.0

`io.codehunters:sdk-jpa` 4.0.0 changes where its exceptions' supertype lives
([ADR 0028](../docs/adr/0028-error-contract-in-its-own-module.md)). Behaviour, codes and messages do
not change.

1. If you catch the supertype, change the import:

   ```diff
   -import io.codehunters.commons.error.ClientInputException;
   +import io.codehunters.errors.ClientInputException;
   ```

   Catching `InvalidSortException`, `InvalidSearchException` or `IllegalArgumentException` needs no
   change.
2. `sdk-commons` is no longer a transitive dependency. If you used its helpers through `sdk-jpa`,
   declare `io.codehunters:sdk-commons:4.0.0`.
3. Use `sdk-web` 4.0.0 or later (the BOM `sdk-bom` 6.0.0 aligns them) so the exceptions are still
   answered with 400.
