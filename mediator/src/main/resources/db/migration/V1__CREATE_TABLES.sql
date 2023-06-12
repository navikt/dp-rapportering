CREATE TABLE IF NOT EXISTS person
(
    id    BIGSERIAL PRIMARY KEY,
    ident VARCHAR(11) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS aktivitet
(
    id           BIGSERIAL PRIMARY KEY,
    uuid         uuid                                                              NOT NULL UNIQUE,
    person_ident VARCHAR(11)                                                       NOT NULL REFERENCES person (ident),
    tilstand     TEXT                                                              NOT NULL,
    opprettet    TIMESTAMP WITH TIME ZONE DEFAULT (NOW() AT TIME ZONE 'utc'::TEXT) NOT NULL,
    dato         DATE                                                              NOT NULL,
    "type"       TEXT                                                              NOT NULL,
    tid          INTERVAL                                                          NOT NULL
);

CREATE TABLE IF NOT EXISTS rapporteringsperiode
(
    id                 BIGSERIAL PRIMARY KEY,
    uuid               uuid                                                              NOT NULL UNIQUE,
    person_ident       VARCHAR(11)                                                       NOT NULL REFERENCES person (ident),
    tilstand           TEXT                                                              NOT NULL,
    opprettet          TIMESTAMP WITH TIME ZONE DEFAULT (NOW() AT TIME ZONE 'utc'::TEXT) NOT NULL,
    rapporteringsfrist DATE                                                              NOT NULL,
    fom                DATE                                                              NOT NULL,
    tom                DATE                                                              NOT NULL
);

CREATE TABLE IF NOT EXISTS dag
(
    rapporteringsperiode_id uuid REFERENCES rapporteringsperiode (uuid),
    aktivitet_id            uuid REFERENCES aktivitet (uuid) ON DELETE CASCADE,
    UNIQUE (rapporteringsperiode_id, aktivitet_id)
);

CREATE TABLE IF NOT EXISTS rapporteringsplikt
(
    id          BIGSERIAL PRIMARY KEY,
    uuid        uuid                                                              NOT NULL UNIQUE,
    person_id   BIGSERIAL                                                         NOT NULL REFERENCES person (id),
    type        TEXT                                                              NOT NULL,
    opprettet   TIMESTAMP WITH TIME ZONE DEFAULT (NOW() AT TIME ZONE 'utc'::TEXT) NOT NULL,
    gjelder_fra TIMESTAMP WITH TIME ZONE DEFAULT (NOW() AT TIME ZONE 'utc'::TEXT) NOT NULL
);

