WITH duplicates AS (SELECT rapporteringsperiode_id, aktivitet_id
                    FROM dag
                    GROUP BY rapporteringsperiode_id, aktivitet_id
                    HAVING COUNT(*) > 1)
DELETE
FROM dag
WHERE (rapporteringsperiode_id, aktivitet_id) IN (SELECT rapporteringsperiode_id, aktivitet_id
                                                  FROM duplicates);

ALTER TABLE dag
    ADD UNIQUE (rapporteringsperiode_id, aktivitet_id)