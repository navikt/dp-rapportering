package no.nav.dagpenger.aktivitetslogg

interface IAktivitetslogg {
    fun info(melding: String, vararg params: Any?)
    fun behov(type: Aktivitet.Behov.Behovtype, melding: String, detaljer: Map<String, Any?> = emptyMap())
    fun harAktiviteter(): Boolean
    fun aktivitetsteller(): Int
    fun behov(): List<Aktivitet.Behov>
    fun barn(): IAktivitetslogg
    fun kontekst(kontekst: Aktivitetskontekst)
    fun kontekst(kontekst: Subaktivitetskontekst)
    fun kontekster(): List<IAktivitetslogg>
    fun toMap(mapper: AktivitetsloggMappingPort): Map<String, List<Map<String, Any>>>
    fun registrer(observer: AktivitetsloggObserver)
}
