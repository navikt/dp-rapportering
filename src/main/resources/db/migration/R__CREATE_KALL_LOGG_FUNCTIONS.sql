-- Denne filen ble opprettet som en R-fil, slik at vi senere kan endre antall dager vi lagrer partisjoner
-- Oppretter en partisjon
CREATE OR REPLACE FUNCTION create_partition(fromDate DATE)
    RETURNS void
    LANGUAGE plpgsql AS
$$
BEGIN
    EXECUTE format(
            'CREATE TABLE IF NOT EXISTS kall_logg_%s PARTITION OF kall_logg FOR VALUES FROM (%L) TO (%L)',
            to_char(fromDate, 'YYYY_MM_DD'),
            to_char(fromDate, 'YYYY-MM-DD'),
            to_char(fromDate + 1, 'YYYY-MM-DD')
        );
END;
$$;

-- Oppretter en partisjon for i morgen og sletter gamle partisjoner
CREATE OR REPLACE FUNCTION manage_partitions()
    RETURNS void
    LANGUAGE plpgsql AS
$$
DECLARE
    rec RECORD;
BEGIN
    -- Partisjon for neste dager
    EXECUTE create_partition(CURRENT_DATE + 1);
    EXECUTE create_partition(CURRENT_DATE + 2);
    EXECUTE create_partition(CURRENT_DATE + 3);

    -- Slett partisjon som er eldre enn 90 dager
    FOR rec IN
        SELECT right(child.relname, 10) AS date_in_name
        FROM pg_inherits
                 JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
                 JOIN pg_class child ON pg_inherits.inhrelid = child.oid
        WHERE parent.relname = 'kall_logg'
          AND child.relname != 'kall_logg_1000_01_01' -- Ikke slett DEFAULT partisjonen
          AND to_date(right(child.relname, 10), 'YYYY_MM_DD') < CURRENT_DATE - 90
        LOOP
            EXECUTE format(
                    'DROP TABLE IF EXISTS kall_logg_%s',
                    rec.date_in_name
                );
        END LOOP;
END;
$$;

-- Ved første kjøring opprett partisjoner for i dag og neste dager
SELECT create_partition(CURRENT_DATE);
SELECT create_partition(CURRENT_DATE + 1);
SELECT create_partition(CURRENT_DATE + 2);
