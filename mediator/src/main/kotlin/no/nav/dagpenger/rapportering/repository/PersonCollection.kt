package no.nav.dagpenger.rapportering.repository

internal class PersonCollection<T>(
    private val collection: MutableMap<String, MutableList<T>> = mutableMapOf(),
) {
    fun hent(ident: String) = collection.getOrPut(ident) { mutableListOf() }
    operator fun plus(other: PersonCollection<T>): PersonCollection<T> {
        collection.putAll(other.collection)
        return this
    }
}
