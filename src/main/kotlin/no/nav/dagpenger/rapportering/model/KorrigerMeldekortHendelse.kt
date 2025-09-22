package no.nav.dagpenger.rapportering.model

import java.time.LocalDate

data class KorrigerMeldekortHendelse(
    val ident: String,
    val originalMeldekortId: String,
    val periode: Periode,
    val dager: List<PeriodeData.PeriodeDag>,
    val kilde: PeriodeData.Kilde,
    val begrunnelse: String,
    val meldedato: LocalDate? = null,
)
