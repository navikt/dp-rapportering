package no.nav.dagpenger.rapportering.service

import no.nav.dagpenger.rapportering.connector.AnsvarligSystem
import no.nav.dagpenger.rapportering.connector.PersonregisterConnector

class PersonregisterService(
    private val personregisterConnector: PersonregisterConnector,
) {
    suspend fun erBekreftelseOvertatt(
        ident: String,
        token: String,
    ): Boolean {
        val personstatus = personregisterConnector.hentPersonstatus(ident, token)

        return personstatus?.overtattBekreftelse ?: false
    }

    suspend fun hentAnsvarligSystem(
        ident: String,
        token: String,
    ): AnsvarligSystem {
        val personstatus = hentPersonstatus(ident, token)

        return personstatus?.ansvarligSystem ?: AnsvarligSystem.ARENA
    }

    suspend fun hentPersonstatus(
        ident: String,
        token: String,
    ) = personregisterConnector.hentPersonstatus(ident, token)

    suspend fun hentSisteSakId(ident: String) = personregisterConnector.hentSisteSakId(ident)
}
