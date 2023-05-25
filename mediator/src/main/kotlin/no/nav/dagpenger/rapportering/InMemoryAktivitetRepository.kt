package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.tidslinje.Aktivitet

class InMemoryAktivitetRepository(
    private val aktiviteter: MutableMap<String, MutableList<Aktivitet>> = mutableMapOf(),
) : AktivitetRepository {
    override fun hentAktiviteter(ident: String) = aktiviteter.getOrElse(ident) { emptyList() }

    override fun leggTilAktiviteter(ident: String, aktiviteter: List<Aktivitet>) =
        this.aktiviteter.getOrPut(ident) { mutableListOf() }.addAll(aktiviteter)
}
