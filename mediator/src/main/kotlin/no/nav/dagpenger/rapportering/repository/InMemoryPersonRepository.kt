package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.Person

internal class InMemoryPersonRepository(private val rapporteringsperiodeRepository: InMemoryRapporteringsperiodeRepository) :
    PersonRepository {
    private val personer: MutableSet<Person> = mutableSetOf()

    override fun hentEllerOpprettPerson(ident: String) =
        hentPerson(ident) ?: Person(ident, rapporteringsperiodeRepository.hentRapporteringsperioder(ident))

    override fun lagre(person: Person) {
        personer.add(person)
    }

    private fun hentPerson(ident: String) = personer.singleOrNull { it.ident == ident }
}
