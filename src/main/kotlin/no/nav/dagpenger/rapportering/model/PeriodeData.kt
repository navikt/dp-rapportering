package no.nav.dagpenger.rapportering.model

import com.fasterxml.jackson.module.kotlin.convertValue
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.model.PeriodeData.PeriodeDag
import java.time.LocalDate
import java.time.LocalDateTime

data class PeriodeData(
    val id: Long,
    val ident: String,
    val periode: Periode,
    val dager: List<PeriodeDag>,
    val kanSendesFra: LocalDate,
    val opprettetAv: OpprettetAv,
    val kilde: Kilde,
    val type: Type,
    val status: String = "Innsendt",
    val innsendtTidspunkt: LocalDateTime,
    // Refererer til originalt meldekort ved korrigering
    val korrigeringAv: Long?,
) {
    enum class OpprettetAv {
        Arena,
        Dagpenger,
    }

    data class Kilde(
        val rolle: Rolle,
        val ident: String,
    )

    enum class Rolle {
        Bruker,
        Saksbehandler,
    }

    enum class Type {
        Original,
        Korrigert,
    }

    data class PeriodeDag(
        val dato: LocalDate,
        val aktiviteter: List<Aktivitet> = emptyList(),
        val dagIndex: Int,
        val meldt: Boolean = true,
    )
}

fun PeriodeData.toMap() = defaultObjectMapper.convertValue<Map<String, Any>>(this)

fun List<Dag>.toPeriodeDager(arbeidssøkerperioder: List<ArbeidssøkerperiodeResponse>): List<PeriodeDag> =
    this.map {
        PeriodeDag(
            dato = it.dato,
            aktiviteter = it.aktiviteter,
            dagIndex = it.dagIndex,
            meldt =
                arbeidssøkerperioder.find { periode ->
                    val fom = periode.startet.tidspunkt
                    val tom = periode.avsluttet
                    !fom.isAfter(it.dato.atStartOfDay()) && (tom == null || tom.tidspunkt.isAfter(it.dato.atStartOfDay()))
                } != null,
        )
    }
