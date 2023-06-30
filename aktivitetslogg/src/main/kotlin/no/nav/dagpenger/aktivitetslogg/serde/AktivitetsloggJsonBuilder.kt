package no.nav.dagpenger.aktivitetslogg.serde

import no.nav.dagpenger.aktivitetslogg.Aktivitet
import no.nav.dagpenger.aktivitetslogg.AktivitetsloggVisitor
import no.nav.dagpenger.aktivitetslogg.IAktivitetslogg
import no.nav.dagpenger.aktivitetslogg.SpesifikkKontekst
import no.nav.dagpenger.aktivitetslogg.Varselkode
import java.util.UUID

class AktivitetsloggJsonBuilder(aktivitetslogg: IAktivitetslogg) : AktivitetsloggVisitor {
    internal val aktiviteter = mutableListOf<Map<String, Any>>()

    init {
        aktivitetslogg.accept(this)
    }

    fun asList(): List<Map<String, Any>> {
        return aktiviteter.toList()
    }

    private enum class Alvorlighetsgrad {
        INFO,
        WARN,
        BEHOV,
        ERROR,
        SEVERE,
    }

    override fun visitInfo(
        id: UUID,
        kontekster: List<SpesifikkKontekst>,
        aktivitet: Aktivitet.Info,
        melding: String,
        tidsstempel: String,
    ) {
        leggTilAktivitet(kontekster, Alvorlighetsgrad.INFO, melding, tidsstempel)
    }

    override fun visitWarn(
        id: UUID,
        kontekster: List<SpesifikkKontekst>,
        aktivitet: Aktivitet.LogiskFeil,
        melding: String,
        tidsstempel: String,
    ) {
        leggTilAktivitet(kontekster, Alvorlighetsgrad.WARN, melding, tidsstempel)
    }

    override fun visitBehov(
        id: UUID,
        kontekster: List<SpesifikkKontekst>,
        aktivitet: Aktivitet.Behov,
        type: Aktivitet.Behov.Behovtype,
        melding: String,
        detaljer: Map<String, Any?>,
        tidsstempel: String,
    ) {
        leggTilBehov(
            kontekster,
            Alvorlighetsgrad.BEHOV,
            type,
            melding,
            detaljer,
            tidsstempel,
        )
    }

    override fun visitVarsel(
        id: UUID,
        kontekster: List<SpesifikkKontekst>,
        varsel: Aktivitet.Varsel,
        kode: Varselkode?,
        melding: String,
        tidsstempel: String,
    ) {
        leggTilAktivitet(kontekster, Alvorlighetsgrad.ERROR, melding, tidsstempel)
    }

    override fun visitFunksjonellFeil(
        id: UUID,
        kontekster: List<SpesifikkKontekst>,
        funksjonellFeil: Aktivitet.FunksjonellFeil,
        melding: String,
        tidsstempel: String,
    ) {
        leggTilAktivitet(kontekster, Alvorlighetsgrad.SEVERE, melding, tidsstempel)
    }

    private fun leggTilAktivitet(
        kontekster: List<SpesifikkKontekst>,
        alvorlighetsgrad: Alvorlighetsgrad,
        melding: String,
        tidsstempel: String,
    ) {
        aktiviteter.add(
            mutableMapOf(
                "kontekster" to map(kontekster),
                "alvorlighetsgrad" to alvorlighetsgrad.name,
                "melding" to melding,
                "detaljer" to emptyMap<String, Any?>(),
                "tidsstempel" to tidsstempel,
            ),
        )
    }

    private fun leggTilBehov(
        kontekster: List<SpesifikkKontekst>,
        alvorlighetsgrad: Alvorlighetsgrad,
        type: Aktivitet.Behov.Behovtype,
        melding: String,
        detaljer: Map<String, Any?>,
        tidsstempel: String,
    ) {
        aktiviteter.add(
            mutableMapOf(
                "kontekster" to map(kontekster),
                "alvorlighetsgrad" to alvorlighetsgrad.name,
                "behovtype" to type.toString(),
                "melding" to melding,
                "detaljer" to detaljer,
                "tidsstempel" to tidsstempel,
            ),
        )
    }

    private fun map(kontekster: List<SpesifikkKontekst>) = kontekster.map {
        mutableMapOf(
            "kontekstType" to it.kontekstType,
            "kontekstMap" to it.kontekstMap,
        )
    }
}
