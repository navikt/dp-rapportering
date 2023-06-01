package no.nav.dagpenger.rapportering

import java.time.LocalDate

object Kalender {
    fun erHelligdag(dato: LocalDate) = dato in norskeHelligdager(dato.year)

    private fun norskeHelligdager(year: Int): List<LocalDate> {
        val holidays = mutableListOf<LocalDate>()
        // Nyttårsaften
        holidays.add(LocalDate.of(year, 1, 1))
        // Påske
        holidays.addAll(påskedager(year))
        // 1. mai - Arbeidernes internasjonale kampdag
        holidays.add(LocalDate.of(year, 5, 1))
        // 17. mai - Grunnlovsdagen
        holidays.add(LocalDate.of(year, 5, 17))
        // Pinse
        holidays.add(påskesøndag(year).plusWeeks(7))
        // Jul
        holidays.add(LocalDate.of(year, 12, 25))
        holidays.add(LocalDate.of(year, 12, 26))

        return holidays
    }

    private fun påskedager(year: Int): List<LocalDate> {
        val holidays = mutableListOf<LocalDate>()
        val easterSunday = påskesøndag(year)
        holidays.add(easterSunday) // Easter Sunday
        holidays.add(easterSunday.minusDays(2)) // Good Friday
        holidays.add(easterSunday.plusDays(1)) // Easter Monday
        return holidays
    }

    private fun påskesøndag(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1

        return LocalDate.of(year, month, day)
    }
}
