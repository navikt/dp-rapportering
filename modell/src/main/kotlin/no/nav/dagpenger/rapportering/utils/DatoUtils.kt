package no.nav.dagpenger.rapportering.utils

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal fun LocalDate.finnFørsteMandagIUken() = this.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

internal fun ClosedRange<LocalDate>.finnSisteLørdagIPerioden() =
    this.start.plusWeeks(
        2,
    ).with(TemporalAdjusters.previous(DayOfWeek.SATURDAY))
