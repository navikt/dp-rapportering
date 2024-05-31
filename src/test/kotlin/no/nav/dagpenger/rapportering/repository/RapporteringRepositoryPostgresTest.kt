package no.nav.dagpenger.rapportering.repository

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.repository.Postgres.dataSource
import no.nav.dagpenger.rapportering.repository.Postgres.withMigratedDb
import org.junit.jupiter.api.Test

class RapporteringRepositoryPostgresTest {
    val rapporteringRepositoryPostgres = RapporteringRepositoryPostgres(dataSource)

    @Test
    fun `kan hente rapporteringsperiode`() {
        withMigratedDb {
            val rapporteringsperiode = rapporteringRepositoryPostgres.hentRapporteringsperioder()

            rapporteringsperiode.size shouldBe 0
        }
    }
}
