CREATE TABLE IF NOT EXISTS opprettede_journalposter
(
    journalpost_id  BIGINT                              PRIMARY KEY,
    rapportering_id BIGINT                              NOT NULL,
    dokumentinfo_id BIGINT                              NOT NULL,
    created         TIMESTAMP DEFAULT current_timestamp NOT NULL
);

CREATE INDEX  IF NOT EXISTS opprettede_journalposter_rapportering_id_index ON opprettede_journalposter (rapportering_id);

CREATE TABLE IF NOT EXISTS midlertidig_lagrede_journalposter
(
    id              CHAR(36)                            PRIMARY KEY,
    journalpost     TEXT                                NOT NULL,
    created         TIMESTAMP DEFAULT current_timestamp NOT NULL,
    retries         INT       DEFAULT 0                 NOT NULL
);
