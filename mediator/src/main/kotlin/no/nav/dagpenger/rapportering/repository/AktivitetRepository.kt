package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import java.util.UUID

interface AktivitetRepository {
    fun hentAktivitet(ident: String, uuid: UUID): Aktivitet
    fun hentAktiviteter(ident: String): List<Aktivitet>
}
