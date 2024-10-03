-------------------------------------------------------------------------------
-- Tabell            : INNSENDINGTIDSPUNKT
-- Beskrivelse       : Tabell som indikerer innsendingsdato for gitt meldeperiode
-- Eksempel          : periode_kode: 202450, verdi: -5
-------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS INNSENDINGTIDSPUNKT
(
    PERIODE_KODE    VARCHAR(6) NOT NULL,
    VERDI           INTEGER,
    PRIMARY KEY (PERIODE_KODE)
);
