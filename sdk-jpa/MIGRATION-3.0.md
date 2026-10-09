# Migrating sdk-jpa 2.x to 3.0

3.0.0 changes no class, property or bean. It narrows support:

- **SQL Server is no longer supported.** PostgreSQL is the only database the SDK is built and
  tested against. The reference DDL in the [README](README.md) is PostgreSQL's (`uuid`,
  `timestamp with time zone`).
- 3.0.0 also carries the 2.1.0 additions listed in the [CHANGELOG](CHANGELOG.md):
  `InvalidSortException` and `InvalidSearchException` are `ClientInputException`s from
  `sdk-commons`, now an `api` dependency.

Bump `sdk-jpa` to 3.0.0, or the `sdk-bom` that manages it. No code changes are needed on
PostgreSQL.
