package no.nav.dagpenger.rapportering.service

import no.nav.dagpenger.rapportering.config.Configuration.unleash
import no.nav.dagpenger.rapportering.connector.PersonregisterConnector
import no.nav.dagpenger.rapportering.connector.Personstatus
import java.time.LocalDate

class PersonregisterService(
    private val personregisterConnector: PersonregisterConnector,
    private val rapporteringService: RapporteringService,
) {
    suspend fun oppdaterPersonstatus(
        ident: String,
        token: String,
    ) {
        // Henter personstatus fra dp-rapportering-personregister
        var personstatus: Personstatus? = null
        if (unleash.isEnabled("send-dp-til-personregister")) {
            personstatus = personregisterConnector.hentPersonstatus(ident, token)
        }

        if (personstatus == Personstatus.IKKE_DAGPENGERBRUKER) {
            val harDp = rapporteringService.harMeldeplikt(ident, token)

            if (harDp == "true") {
                // Sender denne brukeren til dp-rapportering-personregister
                personregisterConnector.oppdaterPersonstatus(ident, token, LocalDate.now())
            }
        }
    }
}
