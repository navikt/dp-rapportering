package no.nav.dagpenger.aktivitetslogg

abstract class Varselkode {
    abstract val varseltekst: String
    internal fun varsel(kontekster: List<SpesifikkKontekst>): Aktivitet.Varsel =
        Aktivitet.Varsel.opprett(kontekster, this, varseltekst)

    /*internal fun funksjonellFeil(kontekster: List<SpesifikkKontekst>): Aktivitet.FunksjonellFeil =
        Aktivitet.FunksjonellFeil.opprett(kontekster, this, funksjonellFeilTekst)*/

    override fun toString() = "${this::class.java.simpleName}: $varseltekst"
}
