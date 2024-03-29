package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.Person
import no.nav.dagpenger.rapportering.Rapporteringsplikt
import java.time.LocalDateTime

interface PersonRepository {
    fun hentEllerOpprettPerson(ident: String): Person

    fun lagre(person: Person)

    fun hentIdenterMedGodkjentPeriode(): List<String>

    fun hentIdenterMedRapporteringsplikt(): List<String>

    fun hentRapporteringspliktFor(ident: String): List<Pair<LocalDateTime, Rapporteringsplikt>>
}
