package no.nav.dagpenger.rapportering.repository

interface TidspunktjusteringRepository {
    suspend fun hentInnsendingtidspunkt(periodeKode: String): Int?

    suspend fun hentSisteFristForTrekkJustering(periodeKode: String): Long?
}
