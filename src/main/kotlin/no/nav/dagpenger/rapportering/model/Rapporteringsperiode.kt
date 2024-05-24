package no.nav.dagpenger.rapportering.model

import no.nav.dagpenger.behandling.api.models.PeriodeResponse
import no.nav.dagpenger.behandling.api.models.RapporteringsperiodeResponse
import java.time.LocalDate

open class Rapporteringsperiode(
    val id: Long,
    val periode: Periode,
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
            kanSendesFra = rapporteringsperiode.kanSendesFra,
            kanSendes = rapporteringsperiode.kanSendes,
            kanKorrigeres = rapporteringsperiode.kanKorrigeres,
        )
    }
