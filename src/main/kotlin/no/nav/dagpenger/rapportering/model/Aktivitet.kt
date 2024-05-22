package no.nav.dagpenger.rapportering.model

import java.time.LocalDate
import java.util.UUID

data class Aktivitet(
    val uuid: UUID,
    val dato: LocalDate,
    val type: AktivitetsType,
)
