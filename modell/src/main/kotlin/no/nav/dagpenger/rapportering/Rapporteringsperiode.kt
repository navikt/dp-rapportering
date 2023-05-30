package no.nav.dagpenger.rapportering

import no.nav.dagpenger.aktivitetslogg.Aktivitetskontekst
import no.nav.dagpenger.aktivitetslogg.IAktivitetslogg
import no.nav.dagpenger.aktivitetslogg.SpesifikkKontekst
import no.nav.dagpenger.rapportering.Foobar.utbetalingshistorikk
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringHendelse
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringsperiodeHendelse
import no.nav.dagpenger.rapportering.hendelser.PersonHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
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
    private val person: Person,
    val rapporteringsperiodeId: UUID,
    private val rapporteringsfrist: LocalDate,
    periode: ClosedRange<LocalDate>,
    private var tilstand: Rapporteringsperiodetilstand,
    private val opprettet: LocalDateTime,
    private var oppdatert: LocalDateTime = opprettet,
) : Aktivitetskontekst {
    private val aktivitetsperiode = person.aktivitetstidslinje.forPeriode(periode)

    // TODO: Må utvides med at perioden blir mandag-mandag
    constructor(
        person: Person,
        rapporteringspliktFom: LocalDate,
    ) : this(person, rapporteringspliktFom, rapporteringspliktFom.plusDays(14))

    internal constructor(
        person: Person,
        fom: LocalDate,
        tom: LocalDate,
    ) : this(
        person = person,
        rapporteringsperiodeId = UUID.randomUUID(),
        rapporteringsfrist = tom,
        periode = fom..tom,
        tilstand = Opprettet,
        opprettet = LocalDateTime.now(),
    )

    fun accept(visitor: RapporteringsperiodVisitor) {
        visitor.visit(this, rapporteringsperiodeId, aktivitetsperiode.periode, this.tilstand.type)
        aktivitetsperiode.accept(visitor)
    }

    fun behandle(hendelse: SøknadInnsendtHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Opprettet ny rapporteringsperiode på grunn av innsendt søknad")
    }

    fun behandle(hendelse: NyRapporteringsperiodeHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Opprettet ny rapporteringsperiode")
    }

    fun behandle(hendelse: NyRapporteringHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Sender inn ny rapportering")

        tilstand.behandle(hendelse, this)
    }

    private sealed interface Rapporteringsperiodetilstand : Aktivitetskontekst {
        val type: TilstandType
        fun entering(rapporteringsperiode: Rapporteringsperiode, hendelse: IAktivitetslogg) {}

        fun behandle(
            hendelse: NyRapporteringHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            throw IllegalStateException("Forventet ikke ny rapportering tilstand ${type.name}")
        }

        fun leaving(rapporteringsperiode: Rapporteringsperiode, hendelse: IAktivitetslogg) {}

        override fun toSpesifikkKontekst() =
            SpesifikkKontekst("Tilstand", mapOf("tilstand" to type.name))
    }

    private object Opprettet : Rapporteringsperiodetilstand {
        override val type = TilstandType.Opprettet
        override fun behandle(
            hendelse: NyRapporteringHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            rapporteringsperiode.aktivitetsperiode.forEach { it.håndter(hendelse) }
            rapporteringsperiode.tilstand(hendelse, Godkjent)
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
            fom = aktivitetsperiode.periode.start,
            tom = aktivitetsperiode.periode.endInclusive,
        )

        person.rapporteringsperiodeEndret(event)
    }

    override fun toSpesifikkKontekst() = SpesifikkKontekst(
        "Rapporteringsperiode",
        mapOf(
            "fom" to aktivitetsperiode.periode.start.toString(),
            "tom" to aktivitetsperiode.periode.endInclusive.toString(),
        ),
    )

    enum class TilstandType {
        Opprettet,
        Godkjent,
        Innsendt,
    }
}
