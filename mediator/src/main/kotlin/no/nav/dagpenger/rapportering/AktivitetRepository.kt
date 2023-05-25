package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.tidslinje.Aktivitet

interface AktivitetRepository {
    fun hentAktiviteter(ident: String): List<Aktivitet>
    fun leggTilAktiviteter(ident: String, aktiviteter: List<Aktivitet>): Boolean
}
