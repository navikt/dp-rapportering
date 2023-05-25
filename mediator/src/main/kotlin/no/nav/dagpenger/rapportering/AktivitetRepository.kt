package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import java.util.UUID

interface AktivitetRepository {
    fun hentAktivitet(ident: String, uuid: UUID): Aktivitet
    fun hentAktiviteter(ident: String): List<Aktivitet>
    fun leggTilAktiviteter(ident: String, aktiviteter: List<Aktivitet>): Boolean
    fun leggTilAktivitet(ident: String, aktivitet: Aktivitet) = leggTilAktiviteter(ident, listOf(aktivitet))
    fun slettAktivitet(ident: String, uuid: UUID): Boolean?
}
