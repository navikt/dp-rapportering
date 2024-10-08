CREATE TABLE IF NOT EXISTS rapporteringsperiode
(
    id                      BIGSERIAL       NOT NULL PRIMARY KEY,
    ident                   VARCHAR(11)     NOT NULL,
    fom                     DATE            NOT NULL,
    tom                     DATE            NOT NULL,
    kan_sendes_fra          DATE            NOT NULL,
    kan_sendes              BOOLEAN         NOT NULL,
    kan_endres              BOOLEAN         NOT NULL,
    brutto_belop            DECIMAL         NULL,
    begrunnelse_endring     VARCHAR         NULL,
    registrert_arbeidssoker BOOLEAN         NULL,
    status                  VARCHAR         NOT NULL,
    original_id             BIGINT          NULL,
    rapporteringstype       VARCHAR         NULL,
    mottatt_dato            DATE            NULL
);

CREATE TABLE IF NOT EXISTS dag
(
    id                      uuid                NOT NULL PRIMARY KEY,
    rapportering_id         BIGINT              NOT NULL REFERENCES rapporteringsperiode(id),
    dato                    DATE                NOT NULL,
    dag_index               INT                 NOT NULL,
    CONSTRAINT rapportering_dag_unik_kobling    UNIQUE (rapportering_id, dag_index)
);

CREATE TABLE IF NOT EXISTS aktivitet
(
    uuid                    uuid            NOT NULL PRIMARY KEY,
    dag_id                  uuid            NOT NULL REFERENCES dag(id),
    type                    VARCHAR         NOT NULL,
    timer                   VARCHAR         NULL
);
