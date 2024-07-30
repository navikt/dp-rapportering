package no.nav.dagpenger.rapportering.model.hendelse

import java.time.LocalDate
import java.util.UUID

data class InnsendtPeriodeHendelse(
    val meldeingsreferanseId: UUID = UUID.randomUUID(),
    val ident: String,
    val rapporteringsperiodeId: UUID,
    val dato: LocalDate = LocalDate.now(),
): PersonHendelse(meldeingsreferanseId, ident)
