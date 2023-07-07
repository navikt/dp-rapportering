CREATE INDEX ON rapporteringsperiode (tilstand);

CREATE INDEX ON rapporteringsperiode (person_ident, uuid);

CREATE INDEX ON rapporteringsplikt (person_id, type);

CREATE INDEX ON dag (rapporteringsperiode_id);

CREATE INDEX ON godkjenning (rapporteringsperiode_id);
