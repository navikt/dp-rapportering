package no.nav.dagpenger.rapportering

import no.nav.dagpenger.aktivitetslogg.Aktivitetskontekst
import no.nav.dagpenger.aktivitetslogg.IAktivitetslogg
import no.nav.dagpenger.aktivitetslogg.SpesifikkKontekst
import no.nav.dagpenger.rapportering.hendelser.AvgodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.BeregningsdatoPassertHendelse
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.KorrigerPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.ManuellInnsendingHendelse
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringssyklusHendelse
import no.nav.dagpenger.rapportering.hendelser.PersonHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.tidslinje.Aktivitetstidslinje
import no.nav.dagpenger.rapportering.utils.finnFørsteMandagIUken
import no.nav.dagpenger.rapportering.utils.finnSisteLørdagIPerioden
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

fun interface FastsettBeregningsdatoStrategi {
    fun beregn(
        fom: LocalDate,
        tom: LocalDate,
    ): LocalDate
}

class Rapporteringsperiode private constructor(
    val rapporteringsperiodeId: UUID,
    private val beregnesEtter: LocalDate,
    private val periode: ClosedRange<LocalDate>,
    private var tilstand: Rapporteringsperiodetilstand,
    private val opprettet: LocalDateTime,
    private var oppdatert: LocalDateTime = opprettet,
    private val tidslinje: Aktivitetstidslinje = Aktivitetstidslinje(periode),
    private val godkjenningslogg: Godkjenningslogg = Godkjenningslogg(),
    private val korrigerer: Rapporteringsperiode? = null,
    private var korrigertAv: Rapporteringsperiode? = null,
) : Aktivitetskontekst {
    private val observers: MutableSet<RapporteringsperiodeObserver> = mutableSetOf()
    val gjelderFra = periode.start
    val kanGodkjennesFra = periode.finnSisteLørdagIPerioden() // kan godkjenne rapportering tidligst natt til siste lørdag i perioden

    constructor(
        rapporteringspliktFom: LocalDate,
        beregningsdato: FastsettBeregningsdatoStrategi,
    ) : this(
        fom = rapporteringspliktFom.finnFørsteMandagIUken(),
        beregningsdato = beregningsdato,
    )

    internal constructor(
        fom: LocalDate,
        tom: LocalDate = fom.plusDays(RAPPORTERINGSPERIODE_LENGDE - 1),
        beregningsdato: FastsettBeregningsdatoStrategi,
    ) : this(
        rapporteringsperiodeId = UUID.randomUUID(),
        beregnesEtter = beregningsdato.beregn(fom, tom),
        periode = fom..tom,
        tilstand = TilUtfylling,
        opprettet = LocalDateTime.now(),
    )

    init {
        korrigerer?.korrigertAv = this
    }

    companion object {
        private const val RAPPORTERINGSPERIODE_LENGDE = 14L

        fun rehydrer(
            rapporteringsperiodeId: UUID,
            beregnesEtter: LocalDate,
            fraOgMed: LocalDate,
            tilOgMed: LocalDate,
            tilstand: TilstandType,
            opprettet: LocalDateTime,
            tidslinje: Aktivitetstidslinje,
            godkjenningslogg: Godkjenningslogg,
            korrigerer: Rapporteringsperiode?,
        ) = Rapporteringsperiode(
            rapporteringsperiodeId,
            beregnesEtter,
            fraOgMed..tilOgMed,
            when (tilstand) {
                TilstandType.TilUtfylling -> TilUtfylling
                TilstandType.Godkjent -> Godkjent
                TilstandType.Innsendt -> Innsendt
            },
            opprettet,
            opprettet,
            tidslinje,
            godkjenningslogg,
            korrigerer,
            null,
        )

        fun List<Rapporteringsperiode>.hentGjeldende(dato: LocalDate = LocalDate.now()): Rapporteringsperiode? {
            return this.filter {
                it.dekkesAv(dato)
            }.singleOrNull {
                it.tilstand == TilUtfylling
            }
        }
    }

    private fun lagKorrigering(): Rapporteringsperiode {
        return Rapporteringsperiode(
            UUID.randomUUID(),
            beregnesEtter = periode.start,
            periode = periode,
            tilstand = TilUtfylling,
            opprettet = LocalDateTime.now(),
            oppdatert = LocalDateTime.now(),
            tidslinje = tidslinje.kopier(),
            korrigerer = this,
        )
    }

    fun finnSisteKorrigering(): Rapporteringsperiode = korrigertAv?.finnSisteKorrigering() ?: this

    fun dekkesAv(dato: LocalDate) = dato in periode

    fun leggTilFritak(dato: LocalDate) {
        tidslinje.leggTilFritak(dato)
    }

    private fun harEndring() = korrigerer?.tidslinje != this.tidslinje

    fun accept(visitor: RapporteringsperiodVisitor) {
        korrigertAv?.accept(visitor)
        visitor.visit(
            this,
            rapporteringsperiodeId,
            periode,
            this.tilstand.type,
            beregnesEtter,
            korrigerer,
            korrigertAv,
        )
        godkjenningslogg.accept(visitor)
        tidslinje.accept(visitor)
    }

    fun behandle(hendelse: NyRapporteringssyklusHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Opprettet ny rapporteringsperiode")
    }

    fun behandle(hendelse: GodkjennPeriodeHendelse): Boolean {
        if (korrigertAv != null) return korrigertAv!!.behandle(hendelse)
        if (hendelse.rapporteringsperiodeId != rapporteringsperiodeId) return false

        if (hendelse.dato.isBefore(kanGodkjennesFra)) {
            throw GodkjenningExcpetion("Kan ikke godkjenne periode, kan tidligst godkjennes $kanGodkjennesFra")
        }

        hendelse.kontekst(this)
        hendelse.info("Godkjenner rapporteringsperiode")

        tilstand.behandle(hendelse, this)
        return true
    }

    fun behandle(hendelse: AvgodkjennPeriodeHendelse): Boolean {
        if (korrigertAv != null) return korrigertAv!!.behandle(hendelse)
        if (hendelse.rapporteringsperiodeId != rapporteringsperiodeId) return false
        hendelse.kontekst(this)
        hendelse.info("Avgodkjenner periode")

        tilstand.behandle(hendelse, this)
        return true
    }

    fun behandle(hendelse: NyAktivitetHendelse): Boolean {
        if (korrigertAv != null) return korrigertAv!!.behandle(hendelse)
        if (hendelse.rapporteringsperiodeId != rapporteringsperiodeId) return false
        hendelse.kontekst(this)
        hendelse.info("Registrerer ny aktivitet")

        tilstand.behandle(hendelse, this)
        return true
    }

    fun behandle(hendelse: SlettAktivitetHendelse): Boolean {
        if (korrigertAv != null) return korrigertAv!!.behandle(hendelse)
        if (hendelse.rapporteringsperiodeId != rapporteringsperiodeId) return false
        hendelse.kontekst(this)
        hendelse.info("Sletter aktivitet")

        tilstand.behandle(hendelse, this)
        return true
    }

    fun behandle(
        hendelse: BeregningsdatoPassertHendelse,
        sakId: UUID,
    ) {
        hendelse.kontekst(this)

        tilstand.behandle(hendelse, this, sakId)
    }

    fun behandle(hendelse: KorrigerPeriodeHendelse): Boolean {
        if (korrigerer == null && hendelse.rapporteringsperiodeId != rapporteringsperiodeId) return false
        hendelse.kontekst(this)

        tilstand.behandle(hendelse, this)
        return true
    }

    fun behandle(hendelse: ManuellInnsendingHendelse): Boolean {
        if (korrigertAv != null) return korrigertAv!!.behandle(hendelse)
        if (hendelse.rapporteringsperiodeId != rapporteringsperiodeId) return false
        hendelse.kontekst(this)

        tilstand.behandle(hendelse, this)
        return true
    }

    private sealed interface Rapporteringsperiodetilstand : Aktivitetskontekst {
        val type: TilstandType

        fun entering(
            rapporteringsperiode: Rapporteringsperiode,
            hendelse: IAktivitetslogg,
        ) {}

        fun behandle(
            hendelse: GodkjennPeriodeHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            throw IllegalStateException("Forventet ikke godkjenning i tilstand ${type.name}")
        }

        fun behandle(
            hendelse: AvgodkjennPeriodeHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            throw IllegalStateException("Kan ikke avgodkjenne periode i tilstand ${type.name}")
        }

        fun behandle(
            hendelse: NyAktivitetHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            throw IllegalStateException("Forventet ikke ny aktivitet i tilstand ${type.name}")
        }

        fun behandle(
            hendelse: SlettAktivitetHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            throw IllegalStateException("Forventet ikke sletting av aktivitet i tilstand ${type.name}")
        }

        fun behandle(
            hendelse: BeregningsdatoPassertHendelse,
            rapporteringsperiode: Rapporteringsperiode,
            sakId: UUID,
        ) {
            // noop
        }

        fun behandle(
            hendelse: KorrigerPeriodeHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            throw IllegalStateException("Forventer ikke korrigering av rapporteringsperiode i tilstand ${type.name}")
        }

        fun leaving(
            rapporteringsperiode: Rapporteringsperiode,
            hendelse: IAktivitetslogg,
        ) {}

        override fun toSpesifikkKontekst() = SpesifikkKontekst("Tilstand", mapOf("tilstand" to type.name))

        fun behandle(
            hendelse: ManuellInnsendingHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            throw IllegalStateException("Forventer manuell innsending av rapporteringsperiode i tilstand ${type.name}")
        }
    }

    private object TilUtfylling : Rapporteringsperiodetilstand {
        override val type = TilstandType.TilUtfylling

        override fun behandle(
            hendelse: GodkjennPeriodeHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            hendelse.kontekst(this)
            if (rapporteringsperiode.korrigerer != null && !rapporteringsperiode.harEndring()) {
                throw IllegalStateException(
                    "Kan ikke godkjenne korrigering uten endringer",
                )
            }
            rapporteringsperiode.godkjenningslogg.leggTil(hendelse.godkjenningsendring)
            rapporteringsperiode.tidslinje.forEach { it.håndter(hendelse) }
            rapporteringsperiode.tilstand(hendelse, Godkjent)
        }

        override fun behandle(
            hendelse: NyAktivitetHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            hendelse.kontekst(this)
            rapporteringsperiode.tidslinje.leggTilAktivitet(hendelse.aktivitet)
        }

        override fun behandle(
            hendelse: SlettAktivitetHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            hendelse.kontekst(this)
            rapporteringsperiode.tidslinje.forEach { it.håndter(hendelse) }
        }

        override fun behandle(
            hendelse: KorrigerPeriodeHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            hendelse.kontekst(this)
            if (rapporteringsperiode.korrigerer == null) {
                throw IllegalStateException(
                    "Kan ikke starte en korrigering av en periode i tilstand ${type.name} som ikke er en korrigering",
                )
            } else {
                hendelse.info("Erstatter påbegynt korrigering")
                rapporteringsperiode.korrigerer.lagKorrigering()
            }
        }
    }

    // Bruker har godkjent, men ikke sendt videre
    private object Godkjent : Rapporteringsperiodetilstand {
        override val type = TilstandType.Godkjent

        override fun behandle(
            hendelse: BeregningsdatoPassertHendelse,
            rapporteringsperiode: Rapporteringsperiode,
            sakId: UUID,
        ) {
            hendelse.kontekst(this)

            if (rapporteringsperiode.beregnesEtter.isAfter(hendelse.beregningsdato)) {
                hendelse.info("Rapporteringsperioden kan først beregnes etter ${rapporteringsperiode.beregnesEtter}")
                return
            }

            hendelse.info("Sender inn godkjent periode med rapporteringsfrist ${hendelse.beregningsdato}")

            rapporteringsperiode.tilstand(hendelse, Innsendt)
            rapporteringsperiode.emitRapporteringsperiodeInnsendt(sakId)
        }

        override fun behandle(
            hendelse: ManuellInnsendingHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            hendelse.kontekst(this)
            hendelse.info("Sender inn godkjent periode manuelt")

            // TODO("Må gå via rapporteringsplikt")
            rapporteringsperiode.tilstand(hendelse, Innsendt)
            // rapporteringsperiode.emitRapporteringsperiodeInnsendt()
        }

        override fun behandle(
            hendelse: AvgodkjennPeriodeHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            hendelse.kontekst(this)
            hendelse.info("Avgodkjenner periode")

            rapporteringsperiode.godkjenningslogg.avgodkjenn(hendelse.godkjenningsendring)
            rapporteringsperiode.tidslinje.forEach { it.håndter(hendelse) }
            rapporteringsperiode.tilstand(hendelse, TilUtfylling)
        }
    }

    // En eller annen hendelse (vedtak fattet eller rapporteringsfrist passert) sender perioden videre
    private object Innsendt : Rapporteringsperiodetilstand {
        override val type = TilstandType.Innsendt

        override fun behandle(
            hendelse: KorrigerPeriodeHendelse,
            rapporteringsperiode: Rapporteringsperiode,
        ) {
            hendelse.kontekst(this)
            hendelse.info("Oppretter korrigering av rapporteringsperiode med id ${rapporteringsperiode.rapporteringsperiodeId}")

            if (rapporteringsperiode.korrigertAv != null) {
                val korrigertAv = requireNotNull(rapporteringsperiode.korrigertAv)
                korrigertAv.behandle(hendelse)
            } else {
                rapporteringsperiode.lagKorrigering()
            }
        }
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
        emitRapporteringsperiodeEndret(hendelse, forrigeTilstand)
        tilstand.entering(this, hendelse)
    }

    private fun emitRapporteringsperiodeEndret(
        hendelse: PersonHendelse,
        forrigeTilstand: Rapporteringsperiodetilstand = tilstand,
    ) {
        val event =
            RapporteringsperiodeObserver.RapporteringsperiodeEndret(
                rapporteringsperiodeId = rapporteringsperiodeId,
                gjeldendeTilstand = tilstand.type,
                forrigeTilstand = forrigeTilstand.type,
                fom = periode.start,
                tom = periode.endInclusive,
            )

        observers.forEach { it.rapporteringsperiodeEndret(event) }
    }

    private fun emitRapporteringsperiodeInnsendt(sakId: UUID) {
        val event =
            RapporteringsperiodeObserver.RapporteringsperiodeInnsendt(
                rapporteringsperiodeId = rapporteringsperiodeId,
                fom = periode.start,
                tom = periode.endInclusive,
                dager = this.tidslinje.toList(),
                sakId = sakId,
                korrigerer = korrigerer?.rapporteringsperiodeId,
            )

        observers.forEach { it.rapporteringsperiodeInnsendt(event) }
    }

    override fun toSpesifikkKontekst() =
        SpesifikkKontekst(
            "Rapporteringsperiode",
            mapOf(
                "fom" to periode.start.toString(),
                "tom" to periode.endInclusive.toString(),
            ),
        )

    fun registrer(observer: RapporteringsperiodeObserver) = observers.add(observer)

    fun gjelderFor(rapporteringspliktFra: LocalDateTime) = gjelderFor(rapporteringspliktFra.toLocalDate())

    fun gjelderFor(rapporteringspliktFra: LocalDate) =
        rapporteringspliktFra in periode || rapporteringspliktFra.isAfter(periode.endInclusive)

    enum class TilstandType {
        TilUtfylling,
        Godkjent,
        Innsendt,
    }
}

class GodkjenningExcpetion(message: String?) : RuntimeException(message)
