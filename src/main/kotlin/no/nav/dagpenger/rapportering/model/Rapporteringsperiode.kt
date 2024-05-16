package no.nav.dagpenger.rapportering.model

import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration

data class Person(
    val ident: String,
    val rapporteringsperioder: List<Rapporteringsperiode>,
)

data class Rapporteringsperiode(
    val ident: String, // TODO: Fjerne ident når Person er tatt i bruk
    val id: Long,
    val periode: Periode,
    val aktivitetstidslinje: Aktivitetstidslinje,
    val kanKorrigeres: Boolean,
)

data class Periode(
    val fra: LocalDate,
    val til: LocalDate,
    val kanSendesFra: LocalDate,
)

data class Aktivitetstidslinje internal constructor(
    private val dager: MutableSet<Dag> = mutableSetOf(),
)

data class Dag(
    internal val dato: LocalDate,
    private val aktiviteter: MutableList<Aktivitet>,
)

sealed class Aktivitet(
    val dato: LocalDate,
    val tid: Duration,
    val type: AktivitetType,
    val uuid: UUID = UUID.randomUUID(),
) {
    enum class AktivitetType {
        Arbeid,
        Syk,
        Utdanning,
        Fravaer,
    }
}
