package no.nav.dagpenger.rapportering.model

import java.time.LocalDate

class Rapporteringsperiode(
    val id: Long,
    val periode: Periode,
    val dager: List<Dag>,
    val kanSendesFra: LocalDate,
    val kanSendes: Boolean,
    val kanKorrigeres: Boolean,
)
