package no.nav.dagpenger.rapportering

import no.nav.dagpenger.aktivitetslogg.AktivitetsloggEventMapper
import no.nav.dagpenger.rapportering.hendelser.PersonHendelse
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection

internal class AktivitetsloggMediator(private val rapidsConnection: RapidsConnection) {

    private val aktivitetsloggEventMapper = AktivitetsloggEventMapper()
    fun håndter(hendelse: PersonHendelse) {
        aktivitetsloggEventMapper.håndter(hendelse) { aktivitetLoggMelding ->
            rapidsConnection.publish(
                JsonMessage.newMessage(
                    aktivitetLoggMelding.eventNavn,
                    aktivitetLoggMelding.innhold,
                ).toJson(),
            )
        }
    }
}
