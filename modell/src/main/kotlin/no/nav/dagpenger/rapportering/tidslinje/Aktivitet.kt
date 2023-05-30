package no.nav.dagpenger.rapportering.tidslinje

import no.nav.dagpenger.rapportering.AktivitetVisitor
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringHendelse
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

sealed class Aktivitet(
    val dato: LocalDate,
    val tid: Duration,
    val type: AktivitetType,
    val uuid: UUID = UUID.randomUUID(),
    private var tilstand: Tilstand = Ny,
) {

    fun accept(visitor: AktivitetVisitor) {
        visitor.visit(dato, tid, type, uuid)
    }

    enum class AktivitetType {
        Arbeid, Syk, Ferie
    }

    companion object {
        fun perDag(aktiviteter: List<Aktivitet>) = aktiviteter.associateBy { it.dato }
    }

    fun dekkesAv(periode: ClosedRange<LocalDate>) = dato in periode

    fun håndter(hendelse: NyRapporteringHendelse) {
        tilstand.behandle(hendelse, this)
    }

    private interface Tilstand {
        fun behandle(hendelse: NyRapporteringHendelse, aktivitet: Aktivitet) {
            throw IllegalStateException("Kan ikke håndtere ${hendelse::class.java.simpleName} i denne tilstanden")
        }
    }

    private object Ny : Tilstand {
        override fun behandle(hendelse: NyRapporteringHendelse, aktivitet: Aktivitet) {
            aktivitet.tilstand = Låst
        }
    }

    private object Låst : Tilstand

    class Arbeid(dato: LocalDate, arbeidstimer: Number) :
        Aktivitet(dato, arbeidstimer.toDouble().hours, AktivitetType.Arbeid)

    class Syk(dato: LocalDate) : Aktivitet(dato, Duration.INFINITE, AktivitetType.Syk)

    class Ferie(dato: LocalDate) : Aktivitet(dato, Duration.INFINITE, AktivitetType.Ferie)
}
