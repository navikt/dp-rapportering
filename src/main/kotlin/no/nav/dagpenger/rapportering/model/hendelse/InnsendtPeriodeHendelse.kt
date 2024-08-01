package no.nav.dagpenger.rapportering.model.hendelse

import no.nav.dagpenger.rapportering.model.Periode
import java.util.UUID

data class InnsendtPeriodeHendelse(
    val meldingsreferanseId: UUID = UUID.randomUUID(),
    val ident: String,
    val rapporteringsperiodeId: Long,
    val periode: Periode,
) : PersonHendelse(meldingsreferanseId, ident)
