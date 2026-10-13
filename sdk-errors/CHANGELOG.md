# Changelog — sdk-errors

## 1.0.0

### Added
- `io.codehunters.errors.ClientInputException`, moved from `sdk-commons` 3.0.0
  (`io.codehunters.commons.error`) unchanged apart from its package: an `IllegalArgumentException`
  with a stable `code()` for input a client got wrong, whose message is safe to show to the client.
  `sdk-web` 4.0.0 answers it with 400; `sdk-jpa` 4.0.0 and `sdk-barcode` 3.0.0 throw subclasses of
  it ([ADR 0028](../docs/adr/0028-error-contract-in-its-own-module.md)).
