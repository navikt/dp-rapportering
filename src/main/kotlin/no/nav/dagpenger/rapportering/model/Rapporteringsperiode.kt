package no.nav.dagpenger.rapportering.model

import no.nav.dagpenger.rapportering.api.models.AktivitetResponse
import no.nav.dagpenger.rapportering.api.models.AktivitetTypeResponse
import no.nav.dagpenger.rapportering.api.models.DagInnerResponse
import no.nav.dagpenger.rapportering.api.models.PeriodeResponse
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeResponse
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeStatusResponse
import java.time.LocalDate

data class Rapporteringsperiode(
    val id: Long,
    val periode: Periode,
    val dager: List<Dag>,
    val kanSendesFra: LocalDate,
    val kanSendes: Boolean,
    val kanEndres: Boolean,
    val bruttoBelop: Double?,
    val begrunnelseEndring: String?,
    val status: RapporteringsperiodeStatus,
    val registrertArbeidssoker: Boolean?,
)

fun List<Rapporteringsperiode>.toResponse(): List<RapporteringsperiodeResponse> =
    this.map { rapporteringsperiode -> rapporteringsperiode.toResponse() }

fun Rapporteringsperiode.toResponse(): RapporteringsperiodeResponse =
    RapporteringsperiodeResponse(
        id = this.id.toString(),
        periode =
            PeriodeResponse(
                fraOgMed = this.periode.fraOgMed,
                tilOgMed = this.periode.tilOgMed,
            ),
        dager =
            dager.map { dag ->
                DagInnerResponse(
                    dato = dag.dato,
                    aktiviteter =
                        dag.aktiviteter.map { aktivitet ->
                            AktivitetResponse(
                                id = aktivitet.id,
                                type =
                                    when (aktivitet.type) {
                                        Aktivitet.AktivitetsType.Arbeid -> AktivitetTypeResponse.Arbeid
                                        Aktivitet.AktivitetsType.Syk -> AktivitetTypeResponse.Syk
                                        Aktivitet.AktivitetsType.Utdanning -> AktivitetTypeResponse.Utdanning
                                        Aktivitet.AktivitetsType.Fravaer -> AktivitetTypeResponse.Fravaer
                                    },
                                timer = aktivitet.timer,
                            )
                        },
                    dagIndex = dag.dagIndex.toBigDecimal(),
                )
            },
        kanSendesFra = this.kanSendesFra,
        kanSendes = this.kanSendes,
        kanEndres = this.kanEndres,
        begrunnelseEndring = this.begrunnelseEndring,
        bruttoBelop = this.bruttoBelop?.toBigDecimal(),
        status =
            when (this.status) {
                RapporteringsperiodeStatus.TilUtfylling -> RapporteringsperiodeStatusResponse.TilUtfylling
                RapporteringsperiodeStatus.Endret -> RapporteringsperiodeStatusResponse.Endret
                RapporteringsperiodeStatus.Innsendt -> RapporteringsperiodeStatusResponse.Innsendt
                RapporteringsperiodeStatus.Ferdig -> RapporteringsperiodeStatusResponse.Ferdig
            },
        registrertArbeidssoker = this.registrertArbeidssoker,
    )
