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
        visitor.visit(this, dato, tid, type, uuid)
    }

    enum class AktivitetType {
        Arbeid, Syk, Ferie, Rapporteringsplikt, IkkeRapporteringsplikt
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
            tid: Number,
            tilstand: String,
        ): Aktivitet {
            return when (AktivitetType.valueOf(type)) {
                AktivitetType.Arbeid -> Arbeid.rehydrer(uuid, dato, tid, tilstand)
                AktivitetType.Syk -> TODO()
                AktivitetType.Ferie -> TODO()
                AktivitetType.Rapporteringsplikt -> TODO()
                AktivitetType.IkkeRapporteringsplikt -> TODO()
            }
        }
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

    class Arbeid private constructor(
        uuid: UUID = UUID.randomUUID(),
        dato: LocalDate,
        arbeidstimer: Number,
        tilstand: Tilstand = Ny,
    ) :
        Aktivitet(dato, arbeidstimer.toDouble().hours, AktivitetType.Arbeid, uuid, tilstand) {
        constructor(dato: LocalDate, arbeidstimer: Number) : this(UUID.randomUUID(), dato, arbeidstimer, Ny)

        companion object {
            fun rehydrer(
                uuid: UUID,
                dato: LocalDate,
                tid: Number,
                tilstand: String,
            ): Arbeid {
                return Arbeid(uuid, dato, tid, tilstand.rehydrerTilstand())
            }
        }
    }

    class Syk(dato: LocalDate) : Aktivitet(dato, Duration.INFINITE, AktivitetType.Syk)

    class Ferie(dato: LocalDate) : Aktivitet(dato, Duration.INFINITE, AktivitetType.Ferie)

    class Rapporteringsplikt(dato: LocalDate) : Aktivitet(dato, Duration.INFINITE, AktivitetType.Rapporteringsplikt)

    class IkkeRapporteringsplikt(dato: LocalDate) :
        Aktivitet(dato, Duration.INFINITE, AktivitetType.IkkeRapporteringsplikt)
}
