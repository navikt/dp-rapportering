package no.nav.dagpenger.rapportering.service

import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.config.Configuration.unleash
import no.nav.dagpenger.rapportering.connector.Brukerstatus
import no.nav.dagpenger.rapportering.connector.PersonregisterConnector
import no.nav.dagpenger.rapportering.connector.Personstatus
import java.time.LocalDate

class PersonregisterService(
    private val personregisterConnector: PersonregisterConnector,
    private val meldepliktService: MeldepliktService,
) {
    fun erBekreftelseOvertatt(
        ident: String,
        token: String,
    ): Boolean {
        var personstatus: Personstatus?

        runBlocking {
            personstatus = personregisterConnector.hentPersonstatus(ident, token)
        }

        return personstatus?.overtattBekreftelse ?: false
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
}
