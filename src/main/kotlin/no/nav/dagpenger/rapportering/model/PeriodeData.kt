package no.nav.dagpenger.rapportering.model

import com.fasterxml.jackson.module.kotlin.convertValue
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.model.PeriodeData.PeriodeDag
import no.nav.dagpenger.rapportering.utils.PeriodeUtils.kanSendesInn
import java.time.LocalDate
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

data class PeriodeData(
    val id: String,
    val ident: String,
    val periode: Periode,
    val dager: List<PeriodeDag>,
    val kanSendesFra: LocalDate,
    val opprettetAv: OpprettetAv,
    val kilde: Kilde?,
    val type: Type,
    val status: String = "Innsendt",
    val innsendtTidspunkt: LocalDateTime?,
    // Refererer til originalt meldekort ved korrigering
    val korrigeringAv: String?,
    val bruttoBelop: Double? = null,
    val begrunnelseEndring: String? = null,
    val registrertArbeidssoker: Boolean? = null,
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

fun List<PeriodeData>?.toRapporteringsperioder(): List<Rapporteringsperiode> =
    this?.map {
        try {
            it.toRapporteringsperiode()
        } catch (e: Exception) {
            logger.error(e) { "Kunne ikke konvertere PeriodeData til Rapporteringsperiode: $it" }
            throw e
        }
    } ?: emptyList()

fun PeriodeData.toRapporteringsperiode(): Rapporteringsperiode {
    val status =
        when (this.status) {
            "TilUtfylling" -> RapporteringsperiodeStatus.TilUtfylling
            "Endret" -> RapporteringsperiodeStatus.Endret
            "Innsendt" -> RapporteringsperiodeStatus.Innsendt
            "Ferdig" -> RapporteringsperiodeStatus.Ferdig
            "Feilet" -> RapporteringsperiodeStatus.Feilet
            else -> throw IllegalStateException("Ukjent status '$status'")
        }

    return Rapporteringsperiode(
        id = this.id,
        type = if (this.type == PeriodeData.Type.Original) "05" else "09",
        periode = Periode(fraOgMed = this.periode.fraOgMed, tilOgMed = this.periode.tilOgMed),
        dager = this.dager.map { it.toDag() },
        kanSendesFra = this.kanSendesFra,
        kanSendes = kanSendesInn(this.kanSendesFra, status, true),
        kanEndres = this.korrigeringAv == null,
        bruttoBelop = this.bruttoBelop,
        status = status,
        mottattDato = this.innsendtTidspunkt?.toLocalDate(),
        begrunnelseEndring = if (this.begrunnelseEndring.isNullOrBlank()) null else this.begrunnelseEndring,
        registrertArbeidssoker = this.registrertArbeidssoker,
        originalId = this.korrigeringAv,
        rapporteringstype = null,
    )
}

fun PeriodeDag.toDag(): Dag = Dag(dato = this.dato, aktiviteter = this.aktiviteter, dagIndex = this.dagIndex)
