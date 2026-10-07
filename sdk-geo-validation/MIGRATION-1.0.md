# Migrating sdk-geo-validation 0.2.0 → 1.0.0

1.0.0 knows nothing about raffles. The service passes its own scope reference and owns the
procedure.

1. **Port.** Inject `GeoFenceValidator` instead of `RaffleGeoValidationPort`:

   ```java
   // 0.2.0
   GeoValidationDecision d = port.canPurchase(raffleId, point, raffle.coverageType());
   // 1.0.0
   GeoDecision d = geoFence.validate(new ZoneRef(raffleId.toString()), point, coverage(raffle));
   ```

   Map the old coverage yourself: `NATIONAL` → `Coverage.UNRESTRICTED`, `DEPARTMENTAL` and `LOCAL` →
   `Coverage.RESTRICTED` (the procedure already knows which zones each raffle has).

2. **Decision.**

   | 0.2.0 | 1.0.0 |
   |---|---|
   | `allowed()` | `allowed()` |
   | `zoneCode()` | `zone().map(MatchedZone::id)` |
   | `zoneName()` | `zone().flatMap(MatchedZone::name)` |
   | `zoneType()` (`MatchedZoneType`) | `zone().flatMap(MatchedZone::type)` (text, e.g. `"MUNICIPALITY"`) |
   | `department()` | `zone().flatMap(MatchedZone::parent)` |
   | `denialReason()` (`OUTSIDE_ALLOWED_ZONE`, or `null`) | `denialReason()` (`Optional`, `OUTSIDE_ZONE` or `ZONE_NOT_FOUND`) |

3. **Configuration.** The adapter is no longer registered just because a `DataSource` exists.
   To keep calling the existing procedure:

   ```yaml
   codehunters:
     geo:
       adapter: sql-server
       sql-server:
         procedure: dbo.sp_geo_can_purchase_raffle
         parameters:
           scope: raffle_id
           zone-id: matched_code
           zone-type: matched_type
           zone-name: matched_name
           zone-parent: matched_department
   ```

   The scope is passed as `NVARCHAR(128)`: the procedure's `@raffle_id UNIQUEIDENTIFIER` converts it
   implicitly from the UUID string. The other default names (`lat`, `lon`, `allowed`) already match.

4. **Property.** `geo.validation.enabled` → `codehunters.geo.enabled` (the old key works until 2.0,
   with a warning).

5. **No zones.** If the procedure leaves `@allowed` as `NULL` when a scope has no zones, the decision
   is now `ZONE_NOT_FOUND`. 0.2.0 treated it as `OUTSIDE_ALLOWED_ZONE`.
