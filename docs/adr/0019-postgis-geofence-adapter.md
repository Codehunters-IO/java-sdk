# 0019 — PostGIS geofence adapter with geodesic coverage

- Status: accepted
- Date: 2026-10-09

## Context

`sdk-geo-validation` 1.0.0 ships two adapters ([ADR 0015](0015-domain-agnostic-geofencing.md)): a
polygon fence over zones the application loads, and a SQL Server stored procedure the application
owns. The polygon fence is planar: edges are straight lines in degrees, so it is accurate for city-
and region-sized zones, rejects edges that cross the antimeridian and cannot enclose a pole. Services
on PostgreSQL had to implement the port themselves or load every zone into memory.

PostGIS answers containment in the database, next to the zones, with a spatial index. It offers
planar predicates on `geometry` and geodesic ones on `geography`.

Options considered:

1. **A stored function, like the SQL Server adapter.** Every service writes and maintains the
   function; the SDK only calls it.
2. **A configured table or view, queried with planar `ST_Covers` on `geometry`.** Same results as
   the polygon fence, same limits.
3. **A configured table or view, queried with `ST_Covers` on `geography`.**

## Decision

Option 3. The adapter reads the scope's zones from an application-owned table or view whose name
and column names are configured, validated as identifiers and double-quoted. Values are always
bound. The zone column may be `geography` or `geometry` in SRID 4326; it is cast to `geography`, and
the point is tested with `ST_Covers`, so:

- edges are great-circle arcs, accurate at any size;
- a zone may cross the antimeridian;
- a point on the border is inside, as with the polygon fence.

One statement tells a scope without zones (`ZONE_NOT_FOUND`) from a point outside them
(`OUTSIDE_ZONE`). When several zones cover the point, the lowest zone id wins, so the answer is
deterministic. The query runs on a template of its own with a query timeout (default 2 s), like the
SQL Server adapter.

## Consequences

- Services keep their zones where they author them; no function to deploy.
- The polygon and PostGIS adapters can disagree near long edges: the polygon fence draws straight
  lines in degrees, PostGIS draws great circles. Zones meant for both should be small or densified.
- A `geography` column with a GIST index lets PostGIS use the index for coverage. A `geometry`
  column is cast on every row of the scope, so the index on the scope column is what keeps it fast.
- A `geometry` column in another SRID fails the cast at query time; the README says to store SRID
  4326.
- No Hibernate or JTS dependency: the adapter needs `spring-jdbc` and the PostgreSQL driver only.
