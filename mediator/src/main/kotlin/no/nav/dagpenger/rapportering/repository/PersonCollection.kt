package no.nav.dagpenger.rapportering.repository

internal class PersonCollection<T>(
    private val collection: MutableMap<String, MutableList<T>> = mutableMapOf(),
) {
    fun hent(ident: String) = collection.getOrPut(ident) { mutableListOf() }
}
