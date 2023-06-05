package no.nav.dagpenger.rapportering.tidslinje

import no.nav.dagpenger.rapportering.AktivitetVisitor
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration

sealed class Aktivitet(
    val dato: LocalDate,
    val tid: Duration,
    val type: AktivitetType,
    val uuid: UUID = UUID.randomUUID(),
    private var tilstand: Tilstand = Åpen,
) {
    enum class AktivitetType {
        Arbeid, Syk, Ferie
    }

    companion object {
        fun perDag(aktiviteter: Collection<Aktivitet>) = aktiviteter.associateBy { it.dato }

        fun rehydrer(
            uuid: UUID,
            dato: LocalDate,
            type: String,
            tid: Duration? = Duration.INFINITE,
            tilstand: String,
        ): Aktivitet {
            val rehydrertTilstand = when (TilstandType.valueOf(tilstand)) {
                TilstandType.Åpen -> Åpen
                TilstandType.Låst -> Låst
            }

            return when (AktivitetType.valueOf(type)) {
                AktivitetType.Arbeid -> Arbeid(dato, tid!!, uuid, rehydrertTilstand)
                AktivitetType.Syk -> Syk(dato, uuid, rehydrertTilstand)
                AktivitetType.Ferie -> Ferie(dato, uuid, rehydrertTilstand)
            }
        }
    }

    fun dekkesAv(periode: ClosedRange<LocalDate>) = dato in periode

    fun håndter(hendelse: GodkjennPeriodeHendelse) {
        tilstand.behandle(hendelse, this)
    }

    val kanSlettes = tilstand.kanSlettes

    enum class TilstandType {
        Åpen, Låst,
    }

    interface Tilstand {
        val type: TilstandType
        fun behandle(hendelse: GodkjennPeriodeHendelse, aktivitet: Aktivitet) {
            throw IllegalStateException("Kan ikke håndtere ${hendelse::class.java.simpleName} i denne tilstanden")
        }

        val kanSlettes: Boolean get() = false
    }

    private object Åpen : Tilstand {
        override val type = TilstandType.Åpen
        override fun behandle(hendelse: GodkjennPeriodeHendelse, aktivitet: Aktivitet) {
            aktivitet.tilstand = Låst
        }

        override val kanSlettes = true
    }

    private object Låst : Tilstand {
        override val type = TilstandType.Låst
    }

    fun accept(visitor: AktivitetVisitor) {
        visitor.visit(this, uuid, dato, tid, type, tilstand.type)
    }

    class Arbeid internal constructor(
        dato: LocalDate,
        arbeidstimer: Duration,
        uuid: UUID = UUID.randomUUID(),
        tilstand: Tilstand = Åpen,
    ) : Aktivitet(dato, arbeidstimer, AktivitetType.Arbeid, uuid, tilstand) {
        constructor(dato: LocalDate, arbeidstimer: String) : this(
            dato,
            Duration.parseIsoString(arbeidstimer),
            UUID.randomUUID(),
            Åpen,
        )

        constructor(dato: LocalDate, arbeidstimer: Int) : this(
            dato,
            Duration.parseIsoString("PT${arbeidstimer}H"),
            UUID.randomUUID(),
            Åpen,
        )
    }

    class Syk(dato: LocalDate, uuid: UUID = UUID.randomUUID(), tilstand: Tilstand = Åpen) :
        Aktivitet(dato, Duration.INFINITE, AktivitetType.Syk, uuid, tilstand)

    class Ferie(dato: LocalDate, uuid: UUID = UUID.randomUUID(), tilstand: Tilstand = Åpen) :
        Aktivitet(dato, Duration.INFINITE, AktivitetType.Ferie, uuid, tilstand)
}
