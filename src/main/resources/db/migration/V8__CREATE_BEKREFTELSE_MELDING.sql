CREATE TABLE IF NOT EXISTS bekreftelsesmelding
(
    id                      uuid        NOT NULL PRIMARY KEY,
    rapporteringsperiode_id VARCHAR(36) NOT NULL,
    ident                   VARCHAR(11) NOT NULL,
    skal_sendes_dato        DATE        NOT NULL,
    sendt_bekreftelse_id    uuid        NULL,
    sendt_timestamp         TIMESTAMP   NULL
);

CREATE INDEX IF NOT EXISTS bekreftelsesmelding_rapporteringsperiode_id_index ON bekreftelsesmelding (rapporteringsperiode_id);
CREATE INDEX IF NOT EXISTS bekreftelsesmelding_ident_index ON bekreftelsesmelding (ident);
CREATE INDEX IF NOT EXISTS bekreftelsesmelding_skal_sendes_dato_index ON bekreftelsesmelding (skal_sendes_dato);
