package no.nav.dagpenger.rapportering.model.hendelse

import java.util.UUID
import no.nav.dagpenger.rapportering.model.Periode

data class InnsendtPeriodeHendelse(
    val meldingsreferanseId: UUID = UUID.randomUUID(),
    val ident: String,
    val rapporteringsperiodeId: UUID,
    val periode: Periode,
): PersonHendelse(meldingsreferanseId, ident)
