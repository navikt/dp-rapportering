CREATE INDEX ON rapporteringsperiode (tilstand);

CREATE INDEX ON rapporteringsperiode (person_ident, uuid);

CREATE INDEX ON rapporteringsplikt (person_id, type);

CREATE INDEX ON dag (rapporteringsperiode_id);

CREATE INDEX ON godkjenningsendring (rapporteringsperiode_id);

CREATE INDEX ON godkjenningsendring (uuid);
