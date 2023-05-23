package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.Person

interface Repository {
    fun hentPerson(ident: String): Person?
}
