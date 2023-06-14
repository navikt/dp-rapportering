ALTER TABLE rapporteringsperiode
    ADD COLUMN korrigerer  uuid NULL REFERENCES rapporteringsperiode (uuid),
    ADD COLUMN korrigert_av uuid NULL REFERENCES rapporteringsperiode (uuid)