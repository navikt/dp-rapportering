package no.nav.dagpenger.rapportering.model

import java.util.UUID

data class Aktivitet(
    val uuid: UUID,
    val type: AktivitetsType,
    val timer: String?,
) {
    enum class AktivitetsType {
        Arbeid,
        Syk,
        Utdanning,
        Fravaer,
    }
}
