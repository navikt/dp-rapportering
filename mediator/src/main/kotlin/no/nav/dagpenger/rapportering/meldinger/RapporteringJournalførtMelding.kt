package no.nav.dagpenger.rapportering.meldinger

import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.hendelser.RapporteringJournalførtHendelse
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext

internal class RapporteringJournalførtMelding(
    packet: JsonMessage,
    override val ident: String,
    private val periodeId: String,
    private val journalpostId: String,
) :
    HendelseMessage(packet) {
    private val rapporteringJournalførtHendelse: RapporteringJournalførtHendelse
        get() {
            return RapporteringJournalførtHendelse(id, ident, opprettet, periodeId, journalpostId)
        }

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        mediator.behandle(rapporteringJournalførtHendelse)
    }
}
