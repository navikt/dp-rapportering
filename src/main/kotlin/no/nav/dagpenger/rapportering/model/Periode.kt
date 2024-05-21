package no.nav.dagpenger.rapportering.model

import java.time.LocalDate

data class Periode(val fraOgMed: LocalDate, val tilOgMed: LocalDate) {
    init {
        require(!fraOgMed.isAfter(tilOgMed)) {
            "Fra og med-dato kan ikke være etter til og med-dato"
        }
    }

    fun inneholder(dato: LocalDate): Boolean {
        return !dato.isBefore(fraOgMed) && !dato.isAfter(tilOgMed)
    }
}
