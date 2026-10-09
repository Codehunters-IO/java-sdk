-- Generic zones for the PostGIS adapter tests. Coordinates are (longitude latitude), SRID 4326.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE SCHEMA geo;

CREATE TABLE geo.zones (
    scope_ref   VARCHAR(128) NOT NULL,
    zone_id     VARCHAR(128) NOT NULL,
    zone_type   VARCHAR(200),
    zone_name   VARCHAR(200),
    zone_parent VARCHAR(200),
    geom        geography(MultiPolygon, 4326) NOT NULL
);
CREATE INDEX idx_zones_scope ON geo.zones (scope_ref);
CREATE INDEX idx_zones_geom ON geo.zones USING GIST (geom);

-- "north": a small city inside a larger region; both cover (1, 1), the lower id wins.
INSERT INTO geo.zones VALUES
    ('north', 'region-1', 'REGION', 'Region', NULL,
     'SRID=4326;MULTIPOLYGON(((-5 -5, 10 -5, 10 10, -5 10, -5 -5)))'),
    ('north', 'city-1', 'CITY', 'Town', 'Region',
     'SRID=4326;MULTIPOLYGON(((0 0, 2 0, 2 2, 0 2, 0 0)))');

-- "edge": one square whose vertex is checked.
INSERT INTO geo.zones VALUES
    ('edge', 'square-1', 'HAMLET', NULL, NULL,
     'SRID=4326;MULTIPOLYGON(((0 0, 2 0, 2 2, 0 2, 0 0)))');

-- "lake": a square with a hole in the middle.
INSERT INTO geo.zones VALUES
    ('lake', 'shore-1', 'SHORE', NULL, NULL,
     'SRID=4326;MULTIPOLYGON(((0 0, 10 0, 10 10, 0 10, 0 0), (4 4, 6 4, 6 6, 4 6, 4 4)))');

-- "pacific": a box around Fiji that crosses the antimeridian (170°E to 170°W).
INSERT INTO geo.zones VALUES
    ('pacific', 'fiji-1', 'ISLANDS', NULL, NULL,
     'SRID=4326;MULTIPOLYGON(((170 -20, -170 -20, -170 -10, 170 -10, 170 -20)))');

-- The same "north" city, in a geometry column instead of a geography one.
CREATE TABLE geo.zones_geometry (
    scope_ref VARCHAR(128) NOT NULL,
    zone_id   VARCHAR(128) NOT NULL,
    boundary  geometry(MultiPolygon, 4326) NOT NULL
);
INSERT INTO geo.zones_geometry VALUES
    ('north', 'city-1', 'SRID=4326;MULTIPOLYGON(((0 0, 2 0, 2 2, 0 2, 0 0)))');

-- Every zone, three seconds late: for the query timeout.
CREATE VIEW geo.slow_zones AS
    SELECT z.* FROM geo.zones z CROSS JOIN LATERAL (SELECT pg_sleep(3)) AS pause;
