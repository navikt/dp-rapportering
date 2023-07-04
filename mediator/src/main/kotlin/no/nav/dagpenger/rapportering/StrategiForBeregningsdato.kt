package no.nav.dagpenger.rapportering

import java.time.LocalDate

internal val strategiForBeregningsdato: FastsettBeregningsdatoStrategi =
    when (Configuration.properties[Configuration.beregningsdato_strategi]) {
        "fom" -> FastsettBeregningsdatoFom
        "tom" -> FastsettBeregningsdatoTom(2)
        else -> FastsettBeregningsdatoTom(2)
    }

private object FastsettBeregningsdatoFom : FastsettBeregningsdatoStrategi {
    override fun beregn(fom: LocalDate, tom: LocalDate) = fom
}

private class FastsettBeregningsdatoTom(private val dagerFør: Long = 2) : FastsettBeregningsdatoStrategi {
    override fun beregn(fom: LocalDate, tom: LocalDate): LocalDate {
        return tom.minusDays(dagerFør)
    }
}
