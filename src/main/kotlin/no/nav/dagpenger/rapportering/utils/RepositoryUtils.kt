package no.nav.dagpenger.rapportering.utils

object RepositoryUtils {
    fun Int.validateRowsAffected(expected: Int = 1) {
        if (this != expected) throw RuntimeException("Expected $expected but got $this")
    }
}
