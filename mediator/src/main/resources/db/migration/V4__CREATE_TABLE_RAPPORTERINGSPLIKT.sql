CREATE TABLE IF NOT EXISTS rapporteringsplikt
(
    id                 BIGSERIAL PRIMARY KEY,
    uuid               uuid                                                              NOT NULL UNIQUE,
    person_id          BIGSERIAL                                                         NOT NULL REFERENCES person (id),
    type               TEXT                                                              NOT NULL,
    opprettet          TIMESTAMP WITH TIME ZONE DEFAULT (NOW() AT TIME ZONE 'utc'::TEXT) NOT NULL
);