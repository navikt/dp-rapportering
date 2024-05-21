package no.nav.dagpenger.rapportering.model

import java.time.LocalDate

data class Aktivitet(
    val dato: LocalDate,
    val type: AktivitetsType,
)
