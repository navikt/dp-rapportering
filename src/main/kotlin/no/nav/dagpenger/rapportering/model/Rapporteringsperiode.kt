package no.nav.dagpenger.rapportering.model

import no.nav.dagpenger.behandling.api.models.AktivitetResponse
import no.nav.dagpenger.behandling.api.models.AktivitetTypeResponse
import no.nav.dagpenger.behandling.api.models.DagerInnerResponse
import no.nav.dagpenger.behandling.api.models.PeriodeResponse
import no.nav.dagpenger.behandling.api.models.RapporteringsperiodeResponse
import java.time.LocalDate

data class Rapporteringsperiode(
    val id: Long,
    val periode: Periode,
    val dager: List<Dag>,
    val kanSendesFra: LocalDate,
    val kanSendes: Boolean,
    val kanKorrigeres: Boolean,
)

fun List<Rapporteringsperiode>.toResponse(): List<RapporteringsperiodeResponse> =
    this.map { rapporteringsperiode ->
        RapporteringsperiodeResponse(
            id = rapporteringsperiode.id.toString(),
            periode =
                PeriodeResponse(
                    fraOgMed = rapporteringsperiode.periode.fraOgMed,
                    tilOgMed = rapporteringsperiode.periode.tilOgMed,
                ),
            dager =
                rapporteringsperiode.dager.map { dag ->
                    DagerInnerResponse(
                        dato = dag.dato,
                        aktiviteter =
                            dag.aktiviteter.map { aktivitet ->
                                AktivitetResponse(
                                    id = aktivitet.uuid,
                                    dato = aktivitet.dato,
                                    type = AktivitetTypeResponse.valueOf(aktivitet.type.name),
                                    timer = aktivitet.timer,
                                )
                            },
                    )
                },
            kanSendesFra = rapporteringsperiode.kanSendesFra,
            kanSendes = rapporteringsperiode.kanSendes,
            kanKorrigeres = rapporteringsperiode.kanKorrigeres,
        )
    }
