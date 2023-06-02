package no.nav.dagpenger.aktivitetslogg

import java.util.UUID

// Visitor for å besøke en aktivitetslogg.
// Kan for eksempel brukes til å serialisere den til JSON
// https://refactoring.guru/design-patterns/visitor
interface AktivitetsloggVisitor {
    fun preVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {}
    fun visitInfo(
        id: UUID,
        kontekster: List<SpesifikkKontekst>,
        aktivitet: Aktivitet.Info,
        melding: String,
        tidsstempel: String,
    ) {
    }
    fun visitWarn(
        id: UUID,
        kontekster: List<SpesifikkKontekst>,
        aktivitet: Aktivitet.LogiskFeil,
        melding: String,
        tidsstempel: String,
    ) {
    }

    fun visitBehov(
        id: UUID,
        kontekster: List<SpesifikkKontekst>,
        aktivitet: Aktivitet.Behov,
        type: Aktivitet.Behov.Behovtype,
        melding: String,
        detaljer: Map<String, Any?>,
        tidsstempel: String,
    ) {
    }

    fun postVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {}
    fun visitVarsel(
        id: UUID,
        kontekster: List<SpesifikkKontekst>,
        varsel: Aktivitet.Varsel,
        kode: Varselkode?,
        melding: String,
        tidsstempel: String,
    ) {}

    fun visitFunksjonellFeil(
        id: UUID,
        kontekster: List<SpesifikkKontekst>,
        funksjonellFeil: Aktivitet.FunksjonellFeil,
        melding: String,
        tidsstempel: String,
    ) {
    }
}
