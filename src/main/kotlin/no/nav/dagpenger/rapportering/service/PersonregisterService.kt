package no.nav.dagpenger.rapportering.service

import no.nav.dagpenger.rapportering.config.Configuration.unleash
import no.nav.dagpenger.rapportering.connector.AnsvarligSystem
import no.nav.dagpenger.rapportering.connector.Brukerstatus
import no.nav.dagpenger.rapportering.connector.PersonregisterConnector
import java.time.LocalDate

class PersonregisterService(
    private val personregisterConnector: PersonregisterConnector,
    private val meldepliktService: MeldepliktService,
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
        val personstatus = personregisterConnector.hentPersonstatus(ident, token)

        return personstatus?.ansvarligSystem ?: AnsvarligSystem.ARENA
    }

    suspend fun oppdaterPersonstatus(
        ident: String,
        token: String,
    ) {
        // Henter personstatus fra dp-rapportering-personregister
        var brukerstatus: Brukerstatus? = null
        if (unleash.isEnabled("send-dp-til-personregister")) {
            brukerstatus = personregisterConnector.hentBrukerstatus(ident, token)
        }

        if (brukerstatus == Brukerstatus.IKKE_DAGPENGERBRUKER) {
            val harDp = meldepliktService.harDpMeldeplikt(ident, token)

            if (harDp == "true") {
                // Sender denne brukeren til dp-rapportering-personregister
                personregisterConnector.oppdaterPersonstatus(ident, token, LocalDate.now())
            }
        }
    }

    suspend fun hentSisteSakId(ident: String) = personregisterConnector.hentSisteSakId(ident)
}
