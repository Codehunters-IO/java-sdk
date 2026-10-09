# Migrating sdk-jpa 2.x to 3.0

3.0.0 changes no class. It narrows support and turns one default around:

- **JPA auditing is opt-in.** `codehunters.jpa.auditing.enabled` now defaults to `false`. In 2.x,
  adding the jar enabled `@EnableJpaAuditing` for every entity of the application and registered
  an `AuditorAware` reading the security context. If your entities extend `AuditableEntity` and
  you relied on the SDK to fill `created_at`, `created_by`, `updated_at` and `updated_by`, set:

  ```yaml
  codehunters:
    jpa:
      auditing:
        enabled: true
  ```

  The SDK then logs `SDK JPA auditing is on` once at startup. Applications that declare
  `@EnableJpaAuditing` themselves are unaffected: the SDK still steps aside. Without the property
  and without your own `@EnableJpaAuditing`, the audit columns stay empty (no error is raised), so
  check them after upgrading.
- **SQL Server is no longer supported.** PostgreSQL is the only database the SDK is built and
  tested against. The reference DDL in the [README](README.md) is PostgreSQL's (`uuid`,
  `timestamp with time zone`).
- 3.0.0 also carries the 2.1.0 additions listed in the [CHANGELOG](CHANGELOG.md):
  `InvalidSortException` and `InvalidSearchException` are `ClientInputException`s from
  `sdk-commons`, now an `api` dependency.

Bump `sdk-jpa` to 3.0.0, or the `sdk-bom` that manages it, and add the property above if you use
the SDK's auditing.
