package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

internal class Aktivitetstidslinje(dager: List<Aktivitet> = emptyList()) {
    private var dager = dager.toMutableList()

    // TODO: Kun brukt i tester - må fjernes
    internal fun håndter(aktivitet: Aktivitet) {
        dager.add(aktivitet)
    }

    fun håndter(hendelse: NyAktivitetHendelse) {
        dager.addAll(hendelse.aktiviteter)
    }

    fun antallAktiviteter() = dager.size
    fun antallDager() = Aktivitet.perDag(dager).size

    fun forPeriode(periode: ClosedRange<LocalDate>) = Aktivitetstidslinje(dager.filter { it.dekkesAv(periode) })
}

sealed class Aktivitet(
    private val dato: LocalDate,
    private val antall: Duration,
    private val type: AktivitetType,
) {
    enum class AktivitetType {
        Arbeid, Syk, TiltakType, FerieType
    }

    companion object {
        fun perDag(aktiviteter: List<Aktivitet>) = aktiviteter.associateBy { it.dato }
    }

    fun dekkesAv(periode: ClosedRange<LocalDate>) = dato in periode

    class Arbeid(dato: LocalDate, arbeidstimer: Number) :
        Aktivitet(dato, arbeidstimer.toDouble().hours, AktivitetType.Arbeid)

    class Syk(dato: LocalDate) : Aktivitet(dato, Duration.INFINITE, AktivitetType.Syk)

    class Tiltak(dato: LocalDate, timer: Number) : Aktivitet(dato, timer.toDouble().hours, AktivitetType.TiltakType)

    class Ferie(dato: LocalDate) : Aktivitet(dato, Duration.INFINITE, AktivitetType.FerieType)
}
