package no.nav.dagpenger.rapportering.connector

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.config.Configuration.unleash
import no.nav.dagpenger.rapportering.connector.AdapterAktivitet.AdapterAktivitetsType.Arbeid
import no.nav.dagpenger.rapportering.connector.AdapterAktivitet.AdapterAktivitetsType.Fravaer
import no.nav.dagpenger.rapportering.connector.AdapterAktivitet.AdapterAktivitetsType.Syk
import no.nav.dagpenger.rapportering.connector.AdapterAktivitet.AdapterAktivitetsType.Utdanning
import no.nav.dagpenger.rapportering.connector.AdapterRapporteringsperiodeStatus.Endret
import no.nav.dagpenger.rapportering.connector.AdapterRapporteringsperiodeStatus.Feilet
import no.nav.dagpenger.rapportering.connector.AdapterRapporteringsperiodeStatus.Ferdig
import no.nav.dagpenger.rapportering.connector.AdapterRapporteringsperiodeStatus.Innsendt
import no.nav.dagpenger.rapportering.connector.AdapterRapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.KortType
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.DurationUnit.HOURS
import kotlin.time.toDuration

private val logger = KotlinLogging.logger {}

data class AdapterRapporteringsperiode(
    val id: Long,
    val type: String,
    val periode: AdapterPeriode,
    val dager: List<AdapterDag>,
    val kanSendesFra: LocalDate,
    val kanSendes: Boolean,
    val kanEndres: Boolean,
    val bruttoBelop: Double?,
    val begrunnelseEndring: String?,
    val status: AdapterRapporteringsperiodeStatus,
    val mottattDato: LocalDate?,
    val registrertArbeidssoker: Boolean?,
)

enum class AdapterRapporteringsperiodeStatus {
    TilUtfylling,
    Endret,
    Innsendt,
    Ferdig,
    Feilet,
}

data class AdapterPeriode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
)

data class AdapterDag(
    val dato: LocalDate,
    val aktiviteter: List<AdapterAktivitet> = emptyList(),
    val dagIndex: Int,
)

data class AdapterAktivitet(
    val uuid: UUID,
    val type: AdapterAktivitetsType,
    val timer: Double?,
) {
    enum class AdapterAktivitetsType {
        Arbeid,
        Syk,
        Utdanning,
        Fravaer,
    }
}

fun List<AdapterRapporteringsperiode>?.toRapporteringsperioder(): List<Rapporteringsperiode> =
    this?.map {
        try {
            it.toRapporteringsperiode()
        } catch (e: Exception) {
            logger.error(e) { "Kunne ikke konvertere AdapterRapporteringsperiode til Rapporteringsperiode: $it" }
            throw e
        }
    } ?: emptyList()

fun AdapterRapporteringsperiode.toRapporteringsperiode(): Rapporteringsperiode =
    Rapporteringsperiode(
        id = this.id.toString(),
        type = type.toKortType(),
        periode = Periode(fraOgMed = this.periode.fraOgMed, tilOgMed = this.periode.tilOgMed),
        dager = this.dager.map { it.toDag() },
        kanSendesFra = this.kanSendesFra,
        kanSendes = this.kanSendes,
        kanEndres = this.kanEndres,
        bruttoBelop = this.bruttoBelop,
        status =
            when (this.status) {
                TilUtfylling -> RapporteringsperiodeStatus.TilUtfylling
                Endret -> RapporteringsperiodeStatus.Endret
                Innsendt -> RapporteringsperiodeStatus.Innsendt
                Ferdig -> RapporteringsperiodeStatus.Ferdig
                Feilet -> RapporteringsperiodeStatus.Feilet
            },
        mottattDato = this.mottattDato,
        begrunnelseEndring = if (this.begrunnelseEndring.isNullOrBlank()) null else this.begrunnelseEndring,
        registrertArbeidssoker = this.registrertArbeidssoker,
        originalId = null,
        rapporteringstype = null,
    )

fun AdapterDag.toDag(): Dag = Dag(dato = this.dato, aktiviteter = this.aktiviteter.map { it.toAktivitet() }, dagIndex = this.dagIndex)

fun AdapterAktivitet.toAktivitet(): Aktivitet =
    Aktivitet(
        id = this.uuid,
        type =
            when (this.type) {
                Arbeid -> AktivitetsType.Arbeid
                Syk -> AktivitetsType.Syk
                Utdanning -> AktivitetsType.Utdanning
                Fravaer -> AktivitetsType.Fravaer
            },
        timer = this.timer?.toDuration(HOURS)?.toIsoString(),
    )

fun List<Rapporteringsperiode>.toAdapterRapporteringsperioder(): List<AdapterRapporteringsperiode> =
    this.map { it.toAdapterRapporteringsperiode(false) }

fun Rapporteringsperiode.toAdapterRapporteringsperiode(overrideRegistrertArbeidssoker: Boolean = true): AdapterRapporteringsperiode =
    AdapterRapporteringsperiode(
        id = this.id.toLong(),
        type = type.toAdapterKortType(),
        periode = AdapterPeriode(fraOgMed = this.periode.fraOgMed, tilOgMed = this.periode.tilOgMed),
        dager = this.dager.map { it.toAdapterDag() },
        kanSendesFra = this.kanSendesFra,
        kanSendes = this.kanSendes,
        kanEndres = this.kanEndres,
        bruttoBelop = this.bruttoBelop,
        begrunnelseEndring = this.begrunnelseEndring,
        status =
            when (this.status) {
                RapporteringsperiodeStatus.TilUtfylling -> TilUtfylling
                RapporteringsperiodeStatus.Endret -> Endret
                RapporteringsperiodeStatus.Innsendt -> Innsendt
                RapporteringsperiodeStatus.Ferdig -> Ferdig
                RapporteringsperiodeStatus.Feilet -> Feilet
                RapporteringsperiodeStatus.Midlertidig -> Feilet // Vi må ikke sende midlertidige perioder til adapter
            },
        mottattDato = this.mottattDato,
        // Sender "true" til adapteren hvis overrideRegistrertArbeidssoker = true (mens det reelle svaret sendes til Team PAW)
        registrertArbeidssoker =
            if (unleash.isEnabled("dp-rapportering-sp5-true") && overrideRegistrertArbeidssoker) {
                true
            } else {
                this.registrertArbeidssoker
            },
    )

fun Dag.toAdapterDag(): AdapterDag =
    AdapterDag(dato = this.dato, aktiviteter = this.aktiviteter.map { it.toAdapterAktivitet() }, dagIndex = this.dagIndex)

fun Aktivitet.toAdapterAktivitet(): AdapterAktivitet =
    AdapterAktivitet(
        uuid = this.id,
        type =
            when (this.type) {
                AktivitetsType.Arbeid -> Arbeid
                AktivitetsType.Syk -> Syk
                AktivitetsType.Utdanning -> Utdanning
                AktivitetsType.Fravaer -> Fravaer
            },
        timer = this.timer?.let { Duration.parseIsoString(this.timer).toDouble(HOURS) },
    )

fun String.toKortType(): KortType =
    when (this) {
        "05" -> KortType.Ordinaert
        "09" -> KortType.Etterregistrert
        "10" -> KortType.Korrigert
        else -> {
            KortType.Ordinaert
        }
    }

fun KortType.toAdapterKortType(): String =
    when (this) {
        KortType.Ordinaert -> "05"
        KortType.Etterregistrert -> "09"
        KortType.Korrigert -> "10"
    }
