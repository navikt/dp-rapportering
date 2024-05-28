package no.nav.dagpenger.rapportering.model

import java.time.LocalDate

class InnsendtRapporteringsperiode(
    id: Long,
    periode: Periode,
    val dager: List<Dag>,
    kanSendesFra: LocalDate,
    kanSendes: Boolean,
    kanKorrigeres: Boolean,
) : Rapporteringsperiode(
        id = PeriodeId(id),
        periode = periode,
        kanSendesFra = kanSendesFra,
        kanSendes = kanSendes,
        kanKorrigeres = kanKorrigeres,
    )
