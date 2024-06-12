package no.nav.dagpenger.rapportering.model

import java.time.LocalDate

data class Periode(val fraOgMed: LocalDate, val tilOgMed: LocalDate) {
    init {

        require(tilOgMed.minusDays(13) == fraOgMed) {
            "Perioden må være 14 dager lang"
        }
        require(fraOgMed.isBefore(tilOgMed)) {
            "Fra og med-dato kan ikke være etter til og med-dato"
        }
    }

    fun inneholder(dato: LocalDate): Boolean {
        return !dato.isBefore(fraOgMed) && !dato.isAfter(tilOgMed)
    }
}
