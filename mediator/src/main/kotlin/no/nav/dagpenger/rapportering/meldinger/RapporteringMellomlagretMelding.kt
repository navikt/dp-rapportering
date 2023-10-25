package no.nav.dagpenger.rapportering.meldinger

import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.MineBehov
import no.nav.dagpenger.rapportering.hendelser.RapporteringMellomlagretHendelse
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext

internal class RapporteringMellomlagretMelding(
    packet: JsonMessage,
    override val ident: String,
    private val periodeId: String,
    private val json: String,
    private val urn: String,
) :
    HendelseMessage(packet) {
    private val rapporteringMellomlagretHendelse: RapporteringMellomlagretHendelse
        get() {
            val hendelse = RapporteringMellomlagretHendelse(id, ident, opprettet, periodeId)
            hendelse.behov(
                MineBehov.JournalføreRapportering,
                "Trenger å journalføre rapportering",
                mapOf(
                    "periodeId" to periodeId,
                    "json" to json,
                    "urn" to urn,
                ),
            )
            return hendelse
        }

    override fun behandle(
        mediator: IHendelseMediator,
        context: MessageContext,
    ) {
        mediator.behandle(rapporteringMellomlagretHendelse)
    }
}
