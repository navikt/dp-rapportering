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
    id             BIGSERIAL PRIMARY KEY,
    uuid           uuid                                                              NOT NULL UNIQUE,
    person_ident   VARCHAR(11)                                                       NOT NULL REFERENCES person (ident),
    tilstand       TEXT                                                              NOT NULL,
    opprettet      TIMESTAMP WITH TIME ZONE DEFAULT (NOW() AT TIME ZONE 'utc'::TEXT) NOT NULL,
    beregnes_etter DATE                                                              NOT NULL,
    fom            DATE                                                              NOT NULL,
    tom            DATE                                                              NOT NULL,
    korrigerer     uuid                                                              NULL REFERENCES rapporteringsperiode (uuid),
    korrigert_av   uuid                                                              NULL REFERENCES rapporteringsperiode (uuid)
);

CREATE TABLE IF NOT EXISTS dag_aktivitet
(
    rapporteringsperiode_id uuid NOT NULL REFERENCES rapporteringsperiode (uuid),
    aktivitet_id            uuid NOT NULL REFERENCES aktivitet (uuid) ON DELETE CASCADE,
    UNIQUE (rapporteringsperiode_id, aktivitet_id)
);

CREATE TABLE IF NOT EXISTS dag
(
    id                      BIGSERIAL PRIMARY KEY,
    rapporteringsperiode_id uuid NOT NULL REFERENCES rapporteringsperiode (uuid) ON DELETE CASCADE,
    dato                    DATE NOT NULL,
    strategi                TEXT NOT NULL,
    UNIQUE (rapporteringsperiode_id, dato)
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

CREATE TABLE utfører
(
    id        BIGSERIAL PRIMARY KEY,
    opprettet TIMESTAMP WITH TIME ZONE DEFAULT (NOW() AT TIME ZONE 'utc'::TEXT) NOT NULL,
    kilde     TEXT                                                              NOT NULL,
    ident     TEXT                                                              NOT NULL,
    UNIQUE (kilde, ident)
);

CREATE TABLE godkjenningsendring
(
    id                      BIGSERIAL PRIMARY KEY,
    uuid                    uuid UNIQUE NOT NULL,
    rapporteringsperiode_id uuid        NOT NULL REFERENCES rapporteringsperiode (uuid) ON DELETE CASCADE,
    opprettet               TIMESTAMP   NOT NULL,
    avgodkjent_av           BIGINT      NULL REFERENCES godkjenningsendring (id) ON DELETE CASCADE,
    begrunnelse             TEXT
);

CREATE TABLE godkjenning_utført_av
(
    id                     BIGSERIAL PRIMARY KEY,
    godkjenningsendring_id uuid UNIQUE NOT NULL REFERENCES godkjenningsendring (uuid) ON DELETE CASCADE,
    utfører                BIGINT      NOT NULL REFERENCES utfører (id)
);
