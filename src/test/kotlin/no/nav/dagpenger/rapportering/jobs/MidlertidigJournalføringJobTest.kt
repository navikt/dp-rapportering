package no.nav.dagpenger.rapportering.jobs

import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.api.ApiTestSetup.Companion.setEnvConfig
import no.nav.dagpenger.rapportering.connector.createMockClient
import no.nav.dagpenger.rapportering.metrics.JobbkjøringMetrikker
import no.nav.dagpenger.rapportering.service.JournalfoeringService
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class MidlertidigJournalføringJobTest {
    private val journalfoeringService: JournalfoeringService = mockk<JournalfoeringService>(relaxed = true)
    private val jobbkjøringMetrikker = mockk<JobbkjøringMetrikker>(relaxed = true)
    private val midlertidigJournalføringJob: MidlertidigJournalføringJob =
        MidlertidigJournalføringJob(
            delay = 0L,
            httpClient = createMockClient(HttpStatusCode.InternalServerError, ""),
            jobbkjøringMetrikker = jobbkjøringMetrikker,
        )

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            setEnvConfig()
        }
    }

    @Test
    fun `start inkrementerer metrikker når jobben kjører OK`() {
        coEvery { journalfoeringService.journalfoerPaaNytt() } returns 1

        midlertidigJournalføringJob.start(journalfoeringService)

        verify(exactly = 1, timeout = 5000) { jobbkjøringMetrikker.jobbSjekketOmDenSkulleKjøre() }
        verify(exactly = 1, timeout = 5000) { jobbkjøringMetrikker.jobbFullfort(duration = any(), affectedRows = 1) }
        verify(exactly = 0, timeout = 5000) { jobbkjøringMetrikker.jobbFeilet() }
    }

    @Test
    fun `start inkrementerer metrikker når jobben feiler`() {
        coEvery { journalfoeringService.journalfoerPaaNytt() } throws RuntimeException()

        midlertidigJournalføringJob.start(journalfoeringService)

        verify(exactly = 1, timeout = 5000) { jobbkjøringMetrikker.jobbSjekketOmDenSkulleKjøre() }
        verify(exactly = 0, timeout = 5000) { jobbkjøringMetrikker.jobbFullfort(duration = any(), affectedRows = any()) }
        verify(exactly = 1, timeout = 5000) { jobbkjøringMetrikker.jobbFeilet() }
    }
}
