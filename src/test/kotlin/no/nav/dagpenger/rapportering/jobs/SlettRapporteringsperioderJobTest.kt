package no.nav.dagpenger.rapportering.jobs

import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.dagpenger.rapportering.api.ApiTestSetup.Companion.setEnvConfig
import no.nav.dagpenger.rapportering.connector.createMockClient
import no.nav.dagpenger.rapportering.service.RapporteringService
import no.nav.dagpenger.rapportering.utils.MetricsTestUtil.meterRegistry
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.LocalTime

class SlettRapporteringsperioderJobTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            setEnvConfig()
        }
    }

    @Test
    fun `skal utføre oppgaver`() {
        val rapporteringService = mockk<RapporteringService>()
        coEvery { rapporteringService.slettMellomlagredeRapporteringsperioder() } returns 0

        val slettRapporteringsperioderJob =
            SlettRapporteringsperioderJob(
                meterRegistry,
                httpClient = createMockClient(HttpStatusCode.InternalServerError, ""),
                rapporteringService = rapporteringService,
            )

        val mockedTime = LocalTime.of(1, 59, 58)
        val mockTimeProvider = TimeProvider { LocalDateTime.now().with(mockedTime) }

        val taskExecutor =
            TaskExecutor(
                scheduledTasks =
                    listOf(
                        ScheduledTask(slettRapporteringsperioderJob, 2, 0),
                    ),
                timeProvider = mockTimeProvider,
            )

        taskExecutor.startExecution()

        // slettMellomlagredeRapporteringsperioder må kalles én gang etter et par sekunder
        coVerify(exactly = 1, timeout = 10000) { rapporteringService.slettMellomlagredeRapporteringsperioder() }

        // slettMellomlagredeRapporteringsperioder må kalles én gang til etter et par sekunder fordi vi har mocked ZonedDateTime
        coVerify(exactly = 2, timeout = 10000) { rapporteringService.slettMellomlagredeRapporteringsperioder() }
    }
}
