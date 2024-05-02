package no.nav.dagpenger.rapportering.modeller

import java.time.LocalDate
import java.util.UUID

data class Rapporteringsperiode(
    val id: UUID,
    val beregnesEtter: LocalDate,
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val status: Rapporteringsperiodetilstand,
    val dager: List<RapporteringsperiodeDag>,
)

data class RapporteringsperiodeDag(
    val dagIndex: Int,
    val dato: LocalDate,
    val muligeAktiviteter: List<AktivitetType>,
    val aktiviteter: List<Aktivitet>,
)

data class Aktivitet(
    val id: UUID,
    val type: AktivitetType,
    val timer: String,
    val dato: LocalDate,
)

enum class AktivitetType {
    Arbeid,
    Syk,
    Ferie,
}

enum class Rapporteringsperiodetilstand {
    TilUtfylling,
    Godkjent,
    Innsendt,
}
