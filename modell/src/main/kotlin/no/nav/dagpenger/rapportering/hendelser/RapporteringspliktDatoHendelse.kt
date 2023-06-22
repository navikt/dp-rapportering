package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class RapporteringspliktDatoHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    internal val opprettet: LocalDateTime,
    ønsketDato: LocalDate,
    søknadInnsendtDato: LocalDate,
) : PersonHendelse(
    meldingsreferanseId,
    ident,
    Aktivitetslogg(),
) {
    internal val gjelderFra = listOf(ønsketDato, søknadInnsendtDato).max()
}
