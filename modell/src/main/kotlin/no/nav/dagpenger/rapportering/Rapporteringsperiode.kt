package no.nav.dagpenger.rapportering

import no.nav.dagpenger.aktivitetslogg.Aktivitetskontekst
import no.nav.dagpenger.aktivitetslogg.IAktivitetslogg
import no.nav.dagpenger.aktivitetslogg.SpesifikkKontekst
import no.nav.dagpenger.rapportering.Foobar.utbetalingshistorikk
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringsperiodeHendelse
import no.nav.dagpenger.rapportering.hendelser.PersonHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.tidslinje.Aktivitetstidslinje
import no.nav.dagpenger.rapportering.utils.finnFørsteMandagIUken
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class Meldeprivate constructor() : Aktivitetskontekst {
    override fun toSpesifikkKontekst() = SpesifikkKontekst("Rapporteringsperiode")

    private fun trengerYtelser(hendelse: IAktivitetslogg) {
        utbetalingshistorikk(hendelse, LocalDate.MIN..LocalDate.MAX)
    }
}

class Rapporteringsperiode private constructor(
    val rapporteringsperiodeId: UUID,
    private val rapporteringsfrist: LocalDate,
    private val periode: ClosedRange<LocalDate>,
    private var tilstand: Rapporteringsperiodetilstand,
    private val opprettet: LocalDateTime,
    private var oppdatert: LocalDateTime = opprettet,
    private val tidslinje: Aktivitetstidslinje = Aktivitetstidslinje(periode),
) : Aktivitetskontekst {
    private val observers: MutableSet<RapporteringsperiodeObserver> = mutableSetOf()

    constructor(rapporteringspliktFom: LocalDate) : this(fom = rapporteringspliktFom.finnFørsteMandagIUken())

    internal constructor(
        fom: LocalDate,
        tom: LocalDate = fom.plusDays(14),
    ) : this(
        rapporteringsperiodeId = UUID.randomUUID(),
        rapporteringsfrist = tom,
        periode = fom..tom,
        tilstand = TilUtfylling,
        opprettet = LocalDateTime.now(),
    )

    companion object {
        fun rehydrer(
            rapporteringsperiodeId: UUID,
            rapporteringsfrist: LocalDate,
            fraOgMed: LocalDate,
            tilOgMed: LocalDate,
            tilstand: TilstandType,
            opprettet: LocalDateTime,
            tidslinje: Aktivitetstidslinje,
        ) = Rapporteringsperiode(
            rapporteringsperiodeId,
            rapporteringsfrist,
            fraOgMed..tilOgMed,
            when (tilstand) {
                TilstandType.TilUtfylling -> TilUtfylling
                TilstandType.Godkjent -> Godkjent
                TilstandType.Innsendt -> Innsendt
            },
            opprettet,
            opprettet,
            tidslinje,
        )
    }

    fun gjelderFor(dato: LocalDate) = dato in periode

    fun erGyldig() = tidslinje.all { it.gyldig() }

    fun leggTilFritak(dato: LocalDate) {}

    fun accept(visitor: RapporteringsperiodVisitor) {
        visitor.visit(this, rapporteringsperiodeId, periode, this.tilstand.type, rapporteringsfrist)
        tidslinje.accept(visitor)
    }

    fun behandle(hendelse: SøknadInnsendtHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Opprettet ny rapporteringsperiode på grunn av innsendt søknad")
        // rapporteringsfristFra = hendelse.fom
        periode.start.datesUntil(hendelse.fom).forEach {
            // TODO: Legg til fritak i perioden fra start til innsendt
        }
    }

    fun behandle(hendelse: NyRapporteringsperiodeHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Opprettet ny rapporteringsperiode")
    }

    fun behandle(hendelse: GodkjennPeriodeHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Sender inn ny rapportering")

        tilstand.behandle(hendelse, this)
    }

    fun behandle(hendelse: NyAktivitetHendelse): Boolean {
        if (hendelse.rapporteringsperiodeId != rapporteringsperiodeId) return false
        hendelse.kontekst(this)
        hendelse.info("Registrerer ny aktivitet")

        tilstand.behandle(hendelse, this)
        return true
    }

    fun behandle(hendelse: SlettAktivitetHendelse): Boolean {
        if (hendelse.rapporteringsperiodeId != rapporteringsperiodeId) return false
        hendelse.kontekst(this)
        hendelse.info("Sletter aktivitet")

        tilstand.behandle(hendelse, this)
        return true
    }

    private sealed interface Rapporteringsperiodetilstand : Aktivitetskontekst {
        val type: TilstandType

        fun entering(rapporteringsperiode: Rapporteringsperiode, hendelse: IAktivitetslogg) {}

        fun behandle(
            hendelse: GodkjennPeriodeHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            throw IllegalStateException("Forventet ikke ny rapportering tilstand ${type.name}")
        }

        fun behandle(
            hendelse: NyAktivitetHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            throw IllegalStateException("Forventet ikke ny aktivitet i tilstand ${type.name}")
        }

        fun behandle(hendelse: SlettAktivitetHendelse, rapporteringsperiode: Rapporteringsperiode) {
            throw IllegalStateException("Forventet ikke sletting av aktivitet i tilstand ${type.name}")
        }

        fun leaving(rapporteringsperiode: Rapporteringsperiode, hendelse: IAktivitetslogg) {}

        override fun toSpesifikkKontekst() =
            SpesifikkKontekst("Tilstand", mapOf("tilstand" to type.name))
    }

    private object TilUtfylling : Rapporteringsperiodetilstand {
        override val type = TilstandType.TilUtfylling

        override fun behandle(
            hendelse: GodkjennPeriodeHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            hendelse.kontekst(this)
            if (!rapporteringsperiode.erGyldig()) throw IllegalStateException("Kan ikke godkjenne en ugyldig periode")

            rapporteringsperiode.tidslinje.forEach { it.håndter(hendelse) }
            rapporteringsperiode.tilstand(hendelse, Godkjent)
        }

        override fun behandle(hendelse: NyAktivitetHendelse, rapporteringsperiode: Rapporteringsperiode) {
            hendelse.kontekst(this)
            rapporteringsperiode.tidslinje.leggTilAktivitet(hendelse.aktivitet)
        }

        override fun behandle(hendelse: SlettAktivitetHendelse, rapporteringsperiode: Rapporteringsperiode) {
            hendelse.kontekst(this)
            rapporteringsperiode.tidslinje.forEach { it.håndter(hendelse) }
        }
    }

    // Bruker har godkjent, men ikke sendt videre
    private object Godkjent : Rapporteringsperiodetilstand {
        override val type = TilstandType.Godkjent
    }

    // En eller annen hendelse (vedtak fattet eller rapporteringsfrist passert) sender perioden videre
    private object Innsendt : Rapporteringsperiodetilstand {
        override val type = TilstandType.Innsendt
    }

    private fun tilstand(
        hendelse: PersonHendelse,
        nyTilstand: Rapporteringsperiodetilstand,
        block: () -> Unit = {},
    ) {
        if (tilstand == nyTilstand) return // Already in this state => ignore
        tilstand.leaving(this, hendelse)
        val forrigeTilstand = tilstand

        tilstand = nyTilstand
        oppdatert = LocalDateTime.now()
        block()

        hendelse.kontekst(tilstand)
        emitVedtaksperiodeEndret(hendelse, forrigeTilstand)
        tilstand.entering(this, hendelse)
    }

    private fun emitVedtaksperiodeEndret(
        hendelse: PersonHendelse,
        forrigeTilstand: Rapporteringsperiodetilstand = tilstand,
    ) {
        val event = RapporteringsperiodeObserver.RapporteringsperiodeEndret(
            rapporteringsperiodeId = rapporteringsperiodeId,
            gjeldendeTilstand = tilstand.type,
            forrigeTilstand = forrigeTilstand.type,
            fom = periode.start,
            tom = periode.endInclusive,
        )

        observers.forEach { it.rapporteringsperiodeEndret(event) }
    }

    override fun toSpesifikkKontekst() = SpesifikkKontekst(
        "Rapporteringsperiode",
        mapOf(
            "fom" to periode.start.toString(),
            "tom" to periode.endInclusive.toString(),
        ),
    )

    fun registrer(observer: RapporteringsperiodeObserver) = observers.add(observer)

    enum class TilstandType {
        TilUtfylling,
        Godkjent,
        Innsendt,
    }
}
