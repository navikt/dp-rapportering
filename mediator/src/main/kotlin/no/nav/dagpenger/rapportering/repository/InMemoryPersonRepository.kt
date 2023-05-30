package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.Person
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import java.util.UUID

class InMemoryPersonRepository(private val rapporteringsperiodeRepository: InMemoryRapporteringsperiodeRepository) :
    PersonRepository {
    private val personer: MutableSet<Person> = mutableSetOf()

    override fun hentPerson(ident: String) = personer.singleOrNull { it.ident == ident }

    override fun hentEllerOpprettPerson(ident: String) =
        hentPerson(ident) ?: Person(
            ident,
            rapporteringsperiodeRepository.hentRapporteringsperioder(ident),
        ).also {
            personer.add(it)
            it.behandle(SøknadInnsendtHendelse(UUID.randomUUID(), ident))
        }
}
