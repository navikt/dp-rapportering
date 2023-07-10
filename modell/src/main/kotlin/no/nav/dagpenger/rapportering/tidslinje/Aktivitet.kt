package no.nav.dagpenger.rapportering.tidslinje

import no.nav.dagpenger.rapportering.AktivitetVisitor
import no.nav.dagpenger.rapportering.hendelser.AvgodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import java.time.LocalDate
import java.util.Objects
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

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
        fun erLåst(aktivitet: Aktivitet) = aktivitet.tilstand == Låst
        fun erSlettet(aktivitet: Aktivitet) = aktivitet.tilstand == Slettet

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
                TilstandType.Slettet -> throw IllegalStateException("Skal aldri rehydrere en slettet aktivitet")
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

    fun håndter(hendelse: AvgodkjennPeriodeHendelse) {
        tilstand.behandle(hendelse, this)
    }

    fun håndter(hendelse: SlettAktivitetHendelse) {
        if (hendelse.aktivitetId != uuid) return
        tilstand.behandle(hendelse, this)
    }

    override fun equals(other: Any?) =
        other is Aktivitet && dato == other.dato && tid == other.tid && type == other.type

    override fun hashCode() = Objects.hash(dato, tid, type)

    enum class TilstandType {
        Åpen, Låst, Slettet
    }

    interface Tilstand {
        val type: TilstandType
        fun behandle(hendelse: GodkjennPeriodeHendelse, aktivitet: Aktivitet) {
            throw IllegalStateException("Kan ikke håndtere ${hendelse::class.java.simpleName} i denne tilstanden")
        }

        fun behandle(hendelse: AvgodkjennPeriodeHendelse, aktivitet: Aktivitet) {
            throw IllegalStateException("Kan ikke håndtere ${hendelse::class.java.simpleName} i denne tilstanden")
        }

        fun behandle(hendelse: SlettAktivitetHendelse, aktivitet: Aktivitet) {
            throw IllegalStateException("Kan ikke håndtere ${hendelse::class.java.simpleName} i denne tilstanden")
        }
    }

    private object Åpen : Tilstand {
        override val type = TilstandType.Åpen
        override fun behandle(hendelse: GodkjennPeriodeHendelse, aktivitet: Aktivitet) {
            aktivitet.tilstand = Låst
        }

        override fun behandle(hendelse: SlettAktivitetHendelse, aktivitet: Aktivitet) {
            aktivitet.tilstand = Slettet
        }
    }

    private object Låst : Tilstand {
        override val type = TilstandType.Låst

        override fun behandle(hendelse: AvgodkjennPeriodeHendelse, aktivitet: Aktivitet) {
            aktivitet.tilstand = Åpen
        }
    }

    private object Slettet : Tilstand {
        override val type = TilstandType.Slettet
    }

    fun accept(visitor: AktivitetVisitor) {
        visitor.visit(this, uuid, dato, tid, type, tilstand.type)
    }

    abstract fun kopier(): Aktivitet

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

        override fun kopier() = Arbeid(dato, tid, UUID.randomUUID(), Åpen)
    }

    class Syk(dato: LocalDate, uuid: UUID = UUID.randomUUID(), tilstand: Tilstand = Åpen) :
        Aktivitet(dato, 1.days, AktivitetType.Syk, uuid, tilstand) {
        override fun kopier() = Syk(dato, UUID.randomUUID(), Åpen)
    }

    class Ferie(dato: LocalDate, uuid: UUID = UUID.randomUUID(), tilstand: Tilstand = Åpen) :
        Aktivitet(dato, 1.days, AktivitetType.Ferie, uuid, tilstand) {
        override fun kopier() = Ferie(dato, UUID.randomUUID(), Åpen)
    }
}
