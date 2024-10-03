package no.nav.dagpenger.rapportering.repository

interface InnsendingtidspunktRepository {
    suspend fun hentInnsendingtidspunkt(periodeKode: String): Int?
}
