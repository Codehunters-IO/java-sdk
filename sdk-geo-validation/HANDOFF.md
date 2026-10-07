# Handoff: sdk-geo-validation

This module is leaving the SDK ([ADR 0003](../docs/adr/0003-retire-geo-validation-from-the-sdk.md)).
It is used only by the raffle service, which should own it from now on. Version `0.2.0` stays
available in GitHub Packages; nothing newer will be published from this repository.

## What the adopting team gets

- `RaffleGeoValidationPort`: `canPurchase(raffleId, GeoPoint, CoverageType)` returning a
  `GeoValidationDecision` (allowed with matched zone code, type, name and department, or denied
  with a `DenialReason`).
- Value types `GeoPoint`, `Latitude`, `Longitude`, `CoverageType`, `MatchedZoneType`.
- `SqlServerGeoValidationAdapter`, which calls `dbo.sp_geo_can_purchase_raffle` and reads its
  OUTPUT parameters through one `SELECT`. `NATIONAL` coverage is allowed without a database call.
  An `allowed` flag without a matched zone is treated as denied.
- `GeoValidationAutoConfiguration`, enabled by default, switched off with
  `geo.validation.enabled=false`, and skipped when the application declares its own port.
- Unit tests for the value types, the auto-configuration and the row mapping.

The stored procedure and the geography tables are not in this repository; they belong to the
raffle service's database.

## Fixed before the handoff

These fixes are on the source here but were not published. The module's version is
`0.2.1-SNAPSHOT`, and the publish task never uploads a snapshot, so the fixed code cannot reach
GitHub Packages as `0.2.0` by mistake (for example if the check for an existing version fails):

- `Latitude` and `Longitude` accepted `NaN`, because `NaN` fails both `<` comparisons of the range
  check. They now reject `NaN` and infinities, and the message no longer contains the coordinate
  (it is the user's location).
- The statement starts with `SET NOCOUNT ON`, so row counts from inside the procedure do not reach
  the driver before the result row.

## Suggested follow-ups for the new owner

- Rename the `geo.validation.*` property to the service's own prefix.
- Replace `@ConditionalOnBean(DataSource.class)` with `@ConditionalOnSingleCandidate`; with two
  `DataSource` beans and no primary, startup fails today.
- An unknown `matched_type` returned by the procedure throws `IllegalArgumentException` from
  `MatchedZoneType.valueOf`; decide whether that should deny instead.
- Add an integration test against SQL Server with the real procedure.

## Removing it from the SDK

Once the raffle service builds with its own copy, delete `sdk-geo-validation/`, remove it from
`settings.gradle.kts` and from `codehuntersBom.excludedModules` in `platform/sdk-bom`, and mark it
retired in the root `README.md`. It is already out of the BOM's constraints.
