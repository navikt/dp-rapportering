-------------------------------------------------------------------------------
-- Tabell            : KALL_LOGG
-- Beskrivelse       : Loggtabell for API-kall og Kafka hendelser.
-------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kall_logg
(
    kall_logg_id   BIGINT GENERATED ALWAYS AS IDENTITY,
    korrelasjon_id VARCHAR(54)                            NOT NULL,
    tidspunkt      TIMESTAMP(6) DEFAULT current_timestamp NOT NULL,
    type           VARCHAR(10)                            NOT NULL,
    kall_retning   VARCHAR(10)                            NOT NULL,
    method         VARCHAR(10),
    operation      VARCHAR(100)                           NOT NULL,
    status         INTEGER,
    kalltid        BIGINT                                 NOT NULL,
    request        TEXT,
    response       TEXT,
    ident          VARCHAR(11),
    logginfo       TEXT
) PARTITION BY RANGE ((tidspunkt::date));

CREATE TABLE kall_logg_1000_01_01 PARTITION OF kall_logg DEFAULT;

-- Indekser
CREATE INDEX IF NOT EXISTS kalo_1 ON kall_logg (operation, kall_retning);

CREATE INDEX IF NOT EXISTS kalo_2 ON kall_logg (korrelasjon_id);

CREATE INDEX IF NOT EXISTS kalo_3 ON kall_logg (status);

CREATE INDEX IF NOT EXISTS kalo_4 ON kall_logg (kall_logg_id);

CREATE INDEX IF NOT EXISTS kalo_5 ON kall_logg (ident);

CREATE INDEX IF NOT EXISTS kalo_6 ON kall_logg (tidspunkt);

-- Constraints
ALTER TABLE kall_logg
    ADD CONSTRAINT type_ck1 CHECK ( type IN ('REST', 'KAFKA') );

ALTER TABLE kall_logg
    ADD CONSTRAINT kall_retning_ck1 CHECK ( kall_retning IN ('INN', 'UT') );

-- Tabell og kolonnekommentarer
COMMENT ON TABLE kall_logg IS 'Loggtabell for API-kall og Kafka hendelser.';

COMMENT ON COLUMN kall_logg.kall_logg_id IS 'Autogenerert sekvens';
COMMENT ON COLUMN kall_logg.korrelasjon_id IS 'Unik ID som kan brukes for å korrelere logginnslag med logging til Kibana.';
COMMENT ON COLUMN kall_logg.tidspunkt IS 'Tidspunkt for når kallet bli mottatt/utført.';
COMMENT ON COLUMN kall_logg.type IS 'Grensesnittype: REST/KAFKA';
COMMENT ON COLUMN kall_logg.kall_retning IS 'Kallretning: INN: API-kall til applikasjonen. UT: Kall til underliggende tjeneste eller hendelser ut på Kafka.';
COMMENT ON COLUMN kall_logg.method IS 'HTTP-metode. (GET, POST osv.)';
COMMENT ON COLUMN kall_logg.operation IS 'REST: Ressursstien (request URI) til kallet. KAFKA: Navn på Kafka-topic.';
COMMENT ON COLUMN kall_logg.status IS 'HTTP-statuskode returnert fra kallet. For Kafka-grensesnitt: N/A.';
COMMENT ON COLUMN kall_logg.kalltid IS 'Målt tid for utførelse av kallet i millisekunder.';
COMMENT ON COLUMN kall_logg.request IS 'Sendt Kafka-hendelse eller REST-kall.';
COMMENT ON COLUMN kall_logg.response IS 'Komplett HTTP-respons m/ status, headere og responsdata.';
COMMENT ON COLUMN kall_logg.ident IS 'FND, DNR eller noe annet som identifiserer brukere';
COMMENT ON COLUMN kall_logg.logginfo IS 'Tilleggsinformasjon til fri bruk. Kan typisk brukes for feilmeldinger, stacktrace eller annet som kan være nyttig å logge.';
