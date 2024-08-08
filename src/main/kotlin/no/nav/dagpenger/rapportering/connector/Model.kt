package no.nav.dagpenger.rapportering.connector

import no.nav.dagpenger.rapportering.connector.AdapterAktivitet.AdapterAktivitetsType.Arbeid
import no.nav.dagpenger.rapportering.connector.AdapterAktivitet.AdapterAktivitetsType.Fravaer
import no.nav.dagpenger.rapportering.connector.AdapterAktivitet.AdapterAktivitetsType.Syk
import no.nav.dagpenger.rapportering.connector.AdapterAktivitet.AdapterAktivitetsType.Utdanning
import no.nav.dagpenger.rapportering.connector.AdapterRapporteringsperiodeStatus.Ferdig
import no.nav.dagpenger.rapportering.connector.AdapterRapporteringsperiodeStatus.Innsendt
import no.nav.dagpenger.rapportering.connector.AdapterRapporteringsperiodeStatus.Korrigert
import no.nav.dagpenger.rapportering.connector.AdapterRapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.DurationUnit.HOURS
import kotlin.time.toDuration

data class AdapterRapporteringsperiode(
    val id: Long,
    val periode: AdapterPeriode,
    val dager: List<AdapterDag>,
    val kanSendesFra: LocalDate,
    val kanSendes: Boolean,
    val kanKorrigeres: Boolean,
    val bruttoBelop: Double?,
    val begrunnelseKorrigering: String?,
    val status: AdapterRapporteringsperiodeStatus,
    val registrertArbeidssoker: Boolean?,
)

enum class AdapterRapporteringsperiodeStatus {
    TilUtfylling,
    Korrigert,
    Innsendt,
    Ferdig,
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
    this?.map { it.toRapporteringsperiode() } ?: emptyList()

fun AdapterRapporteringsperiode.toRapporteringsperiode(): Rapporteringsperiode =
    Rapporteringsperiode(
        id = this.id,
        periode = Periode(fraOgMed = this.periode.fraOgMed, tilOgMed = this.periode.tilOgMed),
        dager = this.dager.map { it.toDag() },
        kanSendesFra = this.kanSendesFra,
        kanSendes = this.kanSendes,
        kanEndres = this.kanKorrigeres,
        bruttoBelop = this.bruttoBelop,
        status =
            when (this.status) {
                TilUtfylling -> RapporteringsperiodeStatus.TilUtfylling
                Korrigert -> RapporteringsperiodeStatus.Endret
                Innsendt -> RapporteringsperiodeStatus.Innsendt
                Ferdig -> RapporteringsperiodeStatus.Ferdig
            },
        begrunnelseEndring = this.begrunnelseKorrigering,
        registrertArbeidssoker = this.registrertArbeidssoker,
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
    this.map { it.toAdapterRapporteringsperiode() }

fun Rapporteringsperiode.toAdapterRapporteringsperiode(): AdapterRapporteringsperiode =
    AdapterRapporteringsperiode(
        id = this.id,
        periode = AdapterPeriode(fraOgMed = this.periode.fraOgMed, tilOgMed = this.periode.tilOgMed),
        dager = this.dager.map { it.toAdapterDag() },
        kanSendesFra = this.kanSendesFra,
        kanSendes = this.kanSendes,
        kanKorrigeres = this.kanEndres,
        bruttoBelop = this.bruttoBelop,
        begrunnelseKorrigering = this.begrunnelseEndring,
        status =
            when (this.status) {
                RapporteringsperiodeStatus.TilUtfylling -> TilUtfylling
                RapporteringsperiodeStatus.Endret -> Korrigert
                RapporteringsperiodeStatus.Innsendt -> Innsendt
                RapporteringsperiodeStatus.Ferdig -> Ferdig
            },
        registrertArbeidssoker = this.registrertArbeidssoker,
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
