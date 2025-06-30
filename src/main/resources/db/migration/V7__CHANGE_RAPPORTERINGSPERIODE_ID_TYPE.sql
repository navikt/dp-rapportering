ALTER TABLE dag DROP CONSTRAINT dag_rapportering_id_fkey;
ALTER TABLE rapporteringsperiode ALTER COLUMN id TYPE VARCHAR(36);
ALTER TABLE rapporteringsperiode ALTER COLUMN original_id TYPE VARCHAR(36);
ALTER TABLE dag ALTER COLUMN rapportering_id TYPE VARCHAR(36);
ALTER TABLE dag ADD CONSTRAINT dag_rapportering_id_fkey FOREIGN KEY (rapportering_id) REFERENCES rapporteringsperiode(id);

DROP INDEX opprettede_journalposter_rapportering_id_index;
ALTER TABLE opprettede_journalposter ALTER COLUMN rapportering_id TYPE VARCHAR(36);
CREATE INDEX opprettede_journalposter_rapportering_id_index ON opprettede_journalposter (rapportering_id);
