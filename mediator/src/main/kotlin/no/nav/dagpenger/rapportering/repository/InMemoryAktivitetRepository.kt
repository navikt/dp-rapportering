package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import java.util.UUID

class InMemoryAktivitetRepository(
    private val aktiviteter: MutableMap<String, MutableList<Aktivitet>> = mutableMapOf(),
) : AktivitetRepository {
    override fun hentAktivitet(ident: String, uuid: UUID): Aktivitet {
        return aktiviteter[ident]?.single { it.uuid == uuid } ?: throw IllegalArgumentException("Mangler tilgang")
    }

    override fun hentAktiviteter(ident: String) = aktiviteter.getOrElse(ident) { emptyList() }
}
