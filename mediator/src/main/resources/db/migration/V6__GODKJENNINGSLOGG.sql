CREATE TABLE IF NOT EXISTS godkjenning
(
    id                      BIGSERIAL PRIMARY KEY,
    uuid                    uuid UNIQUE NOT NULL,
    rapporteringsperiode_id uuid REFERENCES rapporteringsperiode (uuid) ON DELETE CASCADE,
    opprettet               TIMESTAMP   NOT NULL,
    avgodkjent              TIMESTAMP,
    begrunnelse             TEXT

);

CREATE INDEX ON godkjenning (rapporteringsperiode_id);

CREATE TABLE godkjenning_utført_av
(
    id             BIGSERIAL PRIMARY KEY,
    godkjenning_id uuid UNIQUE REFERENCES godkjenning (uuid) ON DELETE CASCADE
);

CREATE TABLE saksbehandler
(
    id               BIGINT PRIMARY KEY REFERENCES godkjenning_utført_av (id) ON DELETE CASCADE,
    saksbehandler_id TEXT UNIQUE
);

CREATE TABLE sluttbruker
(
    id    BIGINT PRIMARY KEY REFERENCES godkjenning_utført_av (id) ON DELETE CASCADE,
    ident TEXT UNIQUE
);
