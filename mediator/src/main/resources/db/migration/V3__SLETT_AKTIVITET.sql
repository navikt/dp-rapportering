ALTER TABLE dag
    DROP CONSTRAINT IF EXISTS dag_aktivitet_id_fkey;

ALTER TABLE dag
    ADD CONSTRAINT dag_aktivitet_id_fkey
        FOREIGN KEY (aktivitet_id)
            REFERENCES aktivitet (uuid)
            ON DELETE CASCADE;