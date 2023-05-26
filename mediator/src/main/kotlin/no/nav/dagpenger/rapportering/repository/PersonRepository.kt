package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.Person

interface PersonRepository {
    fun hentPerson(ident: String): Person?
    fun hentEllerOpprettPerson(ident: String): Person
}
