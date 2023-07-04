package no.nav.dagpenger.rapportering.meldinger

import no.nav.dagpenger.rapportering.FastsettBeregningsdatoStrategi
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.hendelser.RapporteringspliktDatoHendelse
import no.nav.dagpenger.rapportering.strategiForBeregningsdato
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.asLocalDate

internal class RapporteringspliktDatoMelding(packet: JsonMessage, override val ident: String) :
    HendelseMessage(packet) {
    private val beregningsdatoStrategi: FastsettBeregningsdatoStrategi = strategiForBeregningsdato
    private val ønsketDato = packet["@løsning"]["Virkningsdatoer"]["ønsketdato"].asLocalDate()
    private val søknadInnsendtDato = packet["@løsning"]["Søknadstidspunkt"].asLocalDate()
    private val rapporteringspliktDatoHendelse: RapporteringspliktDatoHendelse
        get() = RapporteringspliktDatoHendelse(
            id,
            ident,
            opprettet,
            ønsketDato,
            søknadInnsendtDato,
            beregningsdatoStrategi,
        )

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        mediator.behandle(rapporteringspliktDatoHendelse)
    }
}
