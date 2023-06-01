package no.nav.dagpenger.rapportering.tidslinje

import no.nav.dagpenger.rapportering.AktivitetVisitor
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
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
        visitor.visit(this, dato, tid, type, uuid)
    }

    enum class AktivitetType {
        Arbeid, Syk, Ferie
    }

    companion object {
        fun perDag(aktiviteter: Collection<Aktivitet>) = aktiviteter.associateBy { it.dato }

        private fun String.rehydrerTilstand(): Tilstand {
            return when (this) {
                "Ny" -> Ny
                "Låst" -> Låst
                else -> throw IllegalStateException("Ugyldig tilstan: $this")
            }
        }

        fun rehydrer(
            uuid: UUID,
            dato: LocalDate,
            type: String,
            tid: Duration = Duration.INFINITE,
            tilstand: String,
        ): Aktivitet {
            val rehydrertTilstand = tilstand.rehydrerTilstand()

            return when (AktivitetType.valueOf(type)) {
                AktivitetType.Arbeid -> Arbeid(uuid, dato, tid, rehydrertTilstand)
                AktivitetType.Syk -> Syk(dato, uuid, rehydrertTilstand)
                AktivitetType.Ferie -> Ferie(dato, uuid, rehydrertTilstand)
            }
        }
    }

    fun dekkesAv(periode: ClosedRange<LocalDate>) = dato in periode

    fun håndter(hendelse: GodkjennPeriodeHendelse) {
        tilstand.behandle(hendelse, this)
    }

    interface Tilstand {
        fun behandle(hendelse: GodkjennPeriodeHendelse, aktivitet: Aktivitet) {
            throw IllegalStateException("Kan ikke håndtere ${hendelse::class.java.simpleName} i denne tilstanden")
        }
    }

    private object Ny : Tilstand {
        override fun behandle(hendelse: GodkjennPeriodeHendelse, aktivitet: Aktivitet) {
            aktivitet.tilstand = Låst
        }
    }

    private object Låst : Tilstand

    class Arbeid(
        uuid: UUID = UUID.randomUUID(),
        dato: LocalDate,
        arbeidstimer: Duration,
        tilstand: Tilstand = Ny,
    ) :
        Aktivitet(dato, arbeidstimer, AktivitetType.Arbeid, uuid, tilstand) {
        constructor(dato: LocalDate, arbeidstimer: Number) : this(
            UUID.randomUUID(),
            dato,
            arbeidstimer.toDouble().hours,
            Ny,
        )
    }

    class Syk(dato: LocalDate, uuid: UUID = UUID.randomUUID(), tilstand: Tilstand = Ny) :
        Aktivitet(dato, Duration.INFINITE, AktivitetType.Syk, uuid, tilstand)

    class Ferie(dato: LocalDate, uuid: UUID = UUID.randomUUID(), tilstand: Tilstand = Ny) :
        Aktivitet(dato, Duration.INFINITE, AktivitetType.Ferie, uuid, tilstand)
}
