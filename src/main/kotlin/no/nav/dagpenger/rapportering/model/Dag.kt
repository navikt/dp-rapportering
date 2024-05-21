package no.nav.dagpenger.rapportering.model

import java.time.LocalDate

class Dag(val dato: LocalDate, val aktiviteter: List<Aktivitet> = emptyList())
