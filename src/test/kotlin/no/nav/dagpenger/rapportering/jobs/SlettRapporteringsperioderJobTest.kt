package no.nav.dagpenger.rapportering.jobs

import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.api.ApiTestSetup.Companion.setEnvConfig
import no.nav.dagpenger.rapportering.connector.createMockClient
import no.nav.dagpenger.rapportering.metrics.JobbkjøringMetrikker
import no.nav.dagpenger.rapportering.service.RapporteringService
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class SlettRapporteringsperioderJobTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            setEnvConfig()
        }
    }

    private val rapporteringService = mockk<RapporteringService>()
    private val jobbkjøringMetrikker = mockk<JobbkjøringMetrikker>(relaxed = true)
    private val slettRapporteringsperioderJob =
        SlettRapporteringsperioderJob(
            httpClient = createMockClient(HttpStatusCode.InternalServerError, ""),
            rapporteringService = rapporteringService,
            jobbkjøringMetrikker = jobbkjøringMetrikker,
        )

    @Test
    fun `execute sletter rapporteringsperioder og inkrementerer metrikker`() {
        coEvery { rapporteringService.slettMellomlagredeRapporteringsperioder() } returns 1

        slettRapporteringsperioderJob.execute()

        coVerify(exactly = 1) { rapporteringService.slettMellomlagredeRapporteringsperioder() }
        verify(exactly = 1) { jobbkjøringMetrikker.jobbSjekketOmDenSkulleKjøre() }
        verify(exactly = 1) { jobbkjøringMetrikker.jobbFullfort(duration = any(), affectedRows = 1) }
        verify(exactly = 0) { jobbkjøringMetrikker.jobbFeilet() }
    }

    @Test
    fun `execute inkrementerer metrikker hvis jobben feiler`() {
        coEvery { rapporteringService.slettMellomlagredeRapporteringsperioder() } throws RuntimeException()

        slettRapporteringsperioderJob.execute()

        verify(exactly = 1) { jobbkjøringMetrikker.jobbSjekketOmDenSkulleKjøre() }
        verify(exactly = 0) { jobbkjøringMetrikker.jobbFullfort(duration = any(), affectedRows = any()) }
        verify(exactly = 1) { jobbkjøringMetrikker.jobbFeilet() }
    }
}
