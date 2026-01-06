package no.nav.dagpenger.rapportering.service

import no.nav.dagpenger.rapportering.connector.AdapterRapporteringsperiode
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.Person

class MeldepliktService(
    private val meldepliktConnector: MeldepliktConnector,
) {
    suspend fun harMeldeplikt(
        ident: String,
        token: String,
    ): Boolean =
        meldepliktConnector
            .harMeldeplikt(ident, token)

    suspend fun hentRapporteringsperioder(
        ident: String,
        token: String,
    ): List<AdapterRapporteringsperiode>? =
        meldepliktConnector
            .hentRapporteringsperioder(ident, token)

    suspend fun hentInnsendteRapporteringsperioder(
        ident: String,
        token: String,
    ): List<AdapterRapporteringsperiode>? =
        meldepliktConnector
            .hentInnsendteRapporteringsperioder(ident, token)

    suspend fun hentEndringId(
        originalId: String,
        token: String,
    ): String =
        meldepliktConnector
            .hentEndringId(originalId, token)

    suspend fun sendinnRapporteringsperiode(
        periodeTilInnsending: AdapterRapporteringsperiode,
        token: String,
    ): InnsendingResponse =
        meldepliktConnector
            .sendinnRapporteringsperiode(periodeTilInnsending, token)

    suspend fun hentPerson(
        ident: String,
        token: String,
    ): Person? =
        meldepliktConnector
            .hentPerson(ident, token)
}
