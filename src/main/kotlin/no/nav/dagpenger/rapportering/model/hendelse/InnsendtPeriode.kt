package no.nav.dagpenger.rapportering.model.hendelse

import no.nav.dagpenger.rapportering.model.Periode
import no.nav.helse.rapids_rivers.JsonMessage
import java.util.UUID

data class InnsendtPeriodeHendelse(
    val meldingsreferanseId: UUID = UUID.randomUUID(),
    val ident: String,
    val rapporteringsperiodeId: Long,
    val periode: Periode,
) : PersonHendelse(meldingsreferanseId, ident)

class MeldingOmPeriodeInnsendt(
    private val innsendtPeriodeHendelse: InnsendtPeriodeHendelse,
) {
    fun asMessage(): JsonMessage =
        JsonMessage.newMessage(
            eventName = "rapporteringsperiode_innsendt_hendelse",
            map =
                mapOf(
                    "ident" to innsendtPeriodeHendelse.ident,
                    "rapporteringsperiodeId" to innsendtPeriodeHendelse.rapporteringsperiodeId,
                    "fom" to innsendtPeriodeHendelse.periode.fraOgMed,
                    "tom" to innsendtPeriodeHendelse.periode.tilOgMed,
                ),
        )
}
