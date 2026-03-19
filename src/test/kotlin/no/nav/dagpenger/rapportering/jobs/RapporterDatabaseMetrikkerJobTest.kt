package no.nav.dagpenger.rapportering.jobs

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.metrics.DatabaseMetrikker
import no.nav.dagpenger.rapportering.metrics.JobbkjøringMetrikker
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepository
import no.nav.dagpenger.rapportering.repository.RapporteringRepository
import org.junit.jupiter.api.Test

class RapporterDatabaseMetrikkerJobTest {
    private val rapporteringRepository = mockk<RapporteringRepository>()
    private val journalfoeringRepository = mockk<JournalfoeringRepository>()
    private val databaseMetrikker = mockk<DatabaseMetrikker>(relaxed = true)
    private val jobbkjøringMetrikker = mockk<JobbkjøringMetrikker>(relaxed = true)
    private val rapporterDatabaseMetrikkerJob =
        RapporterDatabaseMetrikkerJob(databaseMetrikker, jobbkjøringMetrikker, 0L)

    @Test
    fun `start oppdaterer databasemetrikker og jobbkjøringmetrikker hvis jobben kjører OK`() {
        coEvery { rapporteringRepository.hentAntallRapporteringsperioder() } returns 50
        coEvery { journalfoeringRepository.hentAntallMidlertidigLagretData() } returns 100

        rapporterDatabaseMetrikkerJob.start(rapporteringRepository, journalfoeringRepository)

        coVerify(exactly = 1, timeout = 5000) { rapporteringRepository.hentAntallRapporteringsperioder() }
        coVerify(exactly = 1, timeout = 5000) { journalfoeringRepository.hentAntallMidlertidigLagretData() }
        verify(exactly = 1, timeout = 5000) {
            databaseMetrikker.oppdater(
                lagredeRapporteringsperioder = 50,
                midlertidigLagredeJournalposter = 100,
            )
        }
        verify(exactly = 1, timeout = 5000) { jobbkjøringMetrikker.jobbSjekketOmDenSkulleKjøre() }
        verify(exactly = 1, timeout = 5000) { jobbkjøringMetrikker.jobbFullfort(any(), any()) }
        verify(exactly = 0, timeout = 5000) { jobbkjøringMetrikker.jobbFeilet() }
    }

    @Test
    fun `start oppdaterer jobbkjøringmetrikker hvis jobben feiler`() {
        coEvery { rapporteringRepository.hentAntallRapporteringsperioder() } throws RuntimeException()

        rapporterDatabaseMetrikkerJob.start(rapporteringRepository, journalfoeringRepository)

        verify(exactly = 1, timeout = 5000) { jobbkjøringMetrikker.jobbSjekketOmDenSkulleKjøre() }
        verify(exactly = 0, timeout = 5000) { jobbkjøringMetrikker.jobbFullfort(any(), any()) }
        verify(exactly = 1, timeout = 5000) { jobbkjøringMetrikker.jobbFeilet() }
    }
}
