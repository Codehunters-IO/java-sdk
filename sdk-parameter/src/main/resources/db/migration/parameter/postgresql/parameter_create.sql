-- Reference DDL for the parameters table, PostgreSQL.
-- Copy it into the application's own migrations; the SDK never runs it. The file name has no
-- Flyway version prefix on purpose, so a classpath:db/migration scan ignores it.
-- The SDK only reads the table: rows are written by the application or an admin tool.

CREATE TABLE parameters (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    module      VARCHAR(64)  NOT NULL,
    submodule   VARCHAR(64)  NOT NULL DEFAULT '',
    name        VARCHAR(128) NOT NULL,
    value       TEXT         NULL,
    type        VARCHAR(16)  NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    description VARCHAR(500) NULL,
    created_by  UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  UUID         NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ  NULL,
    CONSTRAINT pk_parameters PRIMARY KEY (id),
    CONSTRAINT ck_parameters_type CHECK (type IN ('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'JSON'))
);

-- One live row per module, submodule and name; soft-deleted rows may repeat it. Also serves the
-- lookups by module and by module and submodule.
CREATE UNIQUE INDEX ux_parameters_module_submodule_name
    ON parameters (module, submodule, name)
    WHERE deleted_at IS NULL;
