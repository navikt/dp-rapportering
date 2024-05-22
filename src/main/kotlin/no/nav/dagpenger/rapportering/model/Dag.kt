package no.nav.dagpenger.rapportering.model

import java.time.LocalDate

data class Dag(val dato: LocalDate, val aktiviteter: List<Aktivitet> = emptyList())
