package no.nav.dagpenger.rapportering.meldinger

import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.MineBehov
import no.nav.dagpenger.rapportering.hendelser.RapporteringMidlertidigJournalførtHendelse
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import java.util.UUID

internal class RapporteringMidlertidigJournalførtMelding(
    packet: JsonMessage,
    override val ident: String,
    private val periodeId: UUID,
    private val journalpostId: String,
    private val json: String,
) :
    HendelseMessage(packet) {
    private val rapporteringMidlertidigJournalførtHendelse: RapporteringMidlertidigJournalførtHendelse
        get() {
            val hendelse = RapporteringMidlertidigJournalførtHendelse(id, ident, opprettet, periodeId, journalpostId)
            hendelse.behov(
                MineBehov.OpprettPdfForRapportering,
                "Trenger å opprette PDF for å journalføre rapportering",
                mapOf(
                    "periodeId" to periodeId,
                    "journalpostId" to journalpostId,
                    "json" to json,
                ),
            )
            return hendelse
        }

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        mediator.behandle(rapporteringMidlertidigJournalførtHendelse)
    }
}
