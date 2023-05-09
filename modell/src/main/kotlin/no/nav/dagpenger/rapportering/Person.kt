package no.nav.dagpenger.rapportering

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.aktivitetslogg.SpesifikkKontekst
import no.nav.dagpenger.aktivitetslogg.Subaktivitetskontekst
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import java.time.LocalDate

class Person private constructor(
    private val ident: String,
    private val meldeperioder: MutableList<Meldeperiode>,
    override val aktivitetslogg: Aktivitetslogg,
) : Subaktivitetskontekst {
    constructor(ident: String) : this(ident, mutableListOf(), Aktivitetslogg())

    fun behandle(hendelse: SøknadInnsendtHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Behandler søknad innsendt")

        Meldeperiode(
            LocalDate.now(),
            LocalDate.now().plusDays(14),
        ).also {
            meldeperioder.add(it)
            it.behandle(hendelse)
        }
    }

    override fun toSpesifikkKontekst() = SpesifikkKontekst("person", mapOf("ident" to ident))
}
