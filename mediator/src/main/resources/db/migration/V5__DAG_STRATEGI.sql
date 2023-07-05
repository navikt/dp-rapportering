ALTER TABLE dag
    RENAME TO dag_aktivitet;

CREATE TABLE IF NOT EXISTS dag
(
    id                      BIGSERIAL PRIMARY KEY,
    rapporteringsperiode_id uuid REFERENCES rapporteringsperiode (uuid) ON DELETE CASCADE,
    dato                    DATE NOT NULL,
    strategi                TEXT NOT NULL,
    UNIQUE (rapporteringsperiode_id, dato)
);

CREATE INDEX ON dag (rapporteringsperiode_id)