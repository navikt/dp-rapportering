package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetskontekst
import no.nav.dagpenger.aktivitetslogg.IAktivitetslogg
import no.nav.dagpenger.aktivitetslogg.SpesifikkKontekst
import java.util.UUID

abstract class PersonHendelse protected constructor(
    private val meldingsreferanseId: UUID,
    private val ident: String,
    private val aktivitetslogg: IAktivitetslogg,
) : IAktivitetslogg by aktivitetslogg, Aktivitetskontekst {

    init {
        aktivitetslogg.kontekst(this)
    }

    fun ident() = ident

    fun meldingsreferanseId() = meldingsreferanseId

    final override fun toSpesifikkKontekst() = this.javaClass.canonicalName.split('.').last().let {
        SpesifikkKontekst(
            it,
            mapOf(
                "meldingsreferanseId" to meldingsreferanseId().toString(),
                "ident" to ident(),
            ) + kontekst(),
        )
    }

    protected open fun kontekst(): Map<String, String> = emptyMap()
    fun toLogString() = aktivitetslogg.toString()
}
