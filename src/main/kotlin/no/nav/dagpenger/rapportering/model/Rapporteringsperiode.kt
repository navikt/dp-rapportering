package no.nav.dagpenger.rapportering.model

import no.nav.dagpenger.behandling.api.models.PeriodeIdResponse
import no.nav.dagpenger.behandling.api.models.PeriodeResponse
import no.nav.dagpenger.behandling.api.models.RapporteringsperiodeResponse
import java.time.LocalDate

open class Rapporteringsperiode(
    val id: PeriodeId,
    val periode: Periode,
    val kanSendesFra: LocalDate,
    val kanSendes: Boolean,
    val kanKorrigeres: Boolean,
)

fun List<Rapporteringsperiode>.toResponse(): List<RapporteringsperiodeResponse> =
    this.map { rapporteringsperiode -> rapporteringsperiode.toResponse() }

fun Rapporteringsperiode.toResponse(): RapporteringsperiodeResponse =
    RapporteringsperiodeResponse(
        id = PeriodeIdResponse(this.id.value.toString()),
        periode =
            PeriodeResponse(
                fraOgMed = this.periode.fraOgMed,
                tilOgMed = this.periode.tilOgMed,
            ),
        kanSendesFra = this.kanSendesFra,
        kanSendes = this.kanSendes,
        kanKorrigeres = this.kanKorrigeres,
    )
