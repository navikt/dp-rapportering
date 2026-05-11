ALTER TABLE rapporteringsperiode ADD siste_frist_for_trekk DATE NULL;
UPDATE rapporteringsperiode SET siste_frist_for_trekk = tom + INTERVAL '8 days';
ALTER TABLE rapporteringsperiode ALTER COLUMN siste_frist_for_trekk SET NOT NULL;
