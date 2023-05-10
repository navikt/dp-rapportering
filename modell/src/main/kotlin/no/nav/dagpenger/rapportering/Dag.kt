package no.nav.dagpenger.rapportering

import java.time.LocalDate

internal class Dag(
    private val dato: LocalDate,
    private val fravær: Number,
    private val timer: Number,
) {
    internal fun sammenfallerMed(other: Dag) = this.dato == other.dato
}
