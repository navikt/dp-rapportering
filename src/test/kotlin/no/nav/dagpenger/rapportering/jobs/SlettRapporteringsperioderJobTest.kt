package no.nav.dagpenger.rapportering.jobs

import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.dagpenger.rapportering.api.ApiTestSetup.Companion.setEnvConfig
import no.nav.dagpenger.rapportering.connector.createMockClient
import no.nav.dagpenger.rapportering.service.RapporteringService
import no.nav.dagpenger.rapportering.utils.MetricsTestUtil.meterRegistry
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.time.ZonedDateTime

class SlettRapporteringsperioderJobTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            setEnvConfig()
        }
    }

    @Test
    fun test() {
        val rapporteringService = mockk<RapporteringService>()
        coEvery { rapporteringService.slettMellomlagredeRapporteringsperioder() } returns 0

        val slettRapporteringsperioderJob =
            SlettRapporteringsperioderJob(
                meterRegistry,
                httpClient = createMockClient(HttpStatusCode.InternalServerError, ""),
                rapporteringService = rapporteringService,
            )

        val taskExecutor =
            TaskExecutor(
                listOf(
                    ScheduledTask(slettRapporteringsperioderJob, 2, 0),
                ),
            )

        val mockedTime = LocalTime.of(1, 59, 57)
        val mockedDateTime = ZonedDateTime.now().with(mockedTime)
        mockkStatic(ZonedDateTime::class)
        every { ZonedDateTime.of(any(), any()) } returns mockedDateTime

        taskExecutor.startExecution()

        // Venter på taskExecutor
        Thread.sleep(4000)

        // slettMellomlagredeRapporteringsperioder må kalles én gang etter 4 sekunder
        coVerify(exactly = 1) { rapporteringService.slettMellomlagredeRapporteringsperioder() }

        // Venter på taskExecutor
        Thread.sleep(4000)

        // slettMellomlagredeRapporteringsperioder må kalles én gang til etter 4 sekunder fodi vi har mocked ZonedDateTime
        coVerify(exactly = 2) { rapporteringService.slettMellomlagredeRapporteringsperioder() }
    }
}
