-------------------------------------------------------------------------------
-- Tabell            : SISTE_FRIST_FOR_TREKK_JUSTERINGER
-- Beskrivelse       : Tabell som indikerer hvor mange dager sisteFristForTrekk skal justeres for gitt meldeperiode
-- Eksempel          : periode_kode: 202450, verdi: 3
-------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SISTE_FRIST_FOR_TREKK_JUSTERINGER
(
    PERIODE_KODE VARCHAR(6) NOT NULL,
    VERDI        INTEGER    NOT NULL,
    PRIMARY KEY (PERIODE_KODE)
);
