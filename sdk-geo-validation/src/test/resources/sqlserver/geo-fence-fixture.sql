-- Sample zones and procedures for the SQL Server geofence integration test. A real service would
-- use the geography type; bounding boxes keep the fixture independent of spatial support.
CREATE SCHEMA geo
GO
CREATE TABLE geo.zone (
    scope_ref   NVARCHAR(128) NOT NULL,
    priority    INT           NOT NULL,
    zone_id     NVARCHAR(128) NOT NULL,
    zone_type   NVARCHAR(64)  NULL,
    zone_name   NVARCHAR(200) NULL,
    zone_parent NVARCHAR(200) NULL,
    min_lat     FLOAT NOT NULL,
    max_lat     FLOAT NOT NULL,
    min_lon     FLOAT NOT NULL,
    max_lon     FLOAT NOT NULL
)
GO
CREATE TABLE geo.check_log (scope_ref NVARCHAR(128) NOT NULL)
GO
INSERT INTO geo.zone VALUES
    ('north', 1, 'city-1', 'CITY', N'Town', N'Region', 0, 2, 0, 2),
    ('north', 2, 'region-1', 'HAMLET', N'Region', NULL, 0, 10, 0, 10)
GO
-- Custom parameter names: the adapter must map them from configuration.
CREATE PROCEDURE geo.sp_fence_check
    @ref NVARCHAR(128), @latitude FLOAT, @longitude FLOAT,
    @is_allowed BIT OUTPUT, @matched_id NVARCHAR(128) OUTPUT, @matched_kind NVARCHAR(64) OUTPUT,
    @matched_label NVARCHAR(200) OUTPUT, @matched_parent NVARCHAR(200) OUTPUT
AS
BEGIN
    -- A write inside the procedure produces a row count the caller must not see first.
    INSERT INTO geo.check_log (scope_ref) VALUES (@ref);
    IF @ref = 'broken'
    BEGIN
        SET @is_allowed = 1;
        RETURN;
    END
    IF NOT EXISTS (SELECT 1 FROM geo.zone WHERE scope_ref = @ref)
    BEGIN
        SET @is_allowed = NULL;
        RETURN;
    END
    SELECT TOP 1 @matched_id = zone_id, @matched_kind = zone_type, @matched_label = zone_name,
                 @matched_parent = zone_parent
    FROM geo.zone
    WHERE scope_ref = @ref
      AND @latitude BETWEEN min_lat AND max_lat
      AND @longitude BETWEEN min_lon AND max_lon
    ORDER BY priority;
    SET @is_allowed = CASE WHEN @matched_id IS NULL THEN 0 ELSE 1 END;
END
GO
-- Default parameter names and only the required outputs.
CREATE PROCEDURE geo.sp_minimal
    @scope_ref NVARCHAR(128), @lat FLOAT, @lon FLOAT,
    @allowed BIT OUTPUT, @zone_id NVARCHAR(128) OUTPUT
AS
BEGIN
    SELECT TOP 1 @zone_id = zone_id
    FROM geo.zone
    WHERE scope_ref = @scope_ref
      AND @lat BETWEEN min_lat AND max_lat
      AND @lon BETWEEN min_lon AND max_lon
    ORDER BY priority;
    SET @allowed = CASE WHEN @zone_id IS NULL THEN 0 ELSE 1 END;
END
GO
