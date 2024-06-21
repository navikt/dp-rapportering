package no.nav.dagpenger.rapportering.scheduler

import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.service.RapporteringService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.quartz.JobBuilder
import org.quartz.JobDataMap
import org.quartz.Scheduler
import org.quartz.SimpleScheduleBuilder
import org.quartz.TriggerBuilder
import org.quartz.impl.StdSchedulerFactory

class SlettRapporteringsperioderJobTest {
    private lateinit var scheduler: Scheduler
    private val rapporteringService = mockk<RapporteringService>()

    @BeforeEach
    fun setUp() {
        scheduler = StdSchedulerFactory().scheduler
        scheduler.start()
    }

    @AfterEach
    fun tearDown() {
        scheduler.shutdown(true)
    }

    @Test
    fun `test scheduler schedules and executes job`() {
        justRun { rapporteringService.slettMellomlagredeRapporteringsperioder() }

        val jobDataMap =
            JobDataMap().apply {
                put("rapporteringService", rapporteringService)
            }

        val job =
            JobBuilder
                .newJob(SlettRapporteringsperioderJob::class.java)
                .withIdentity("testJob")
                .setJobData(jobDataMap)
                .build()

        val trigger =
            TriggerBuilder
                .newTrigger()
                .withIdentity("testTrigger")
                .startNow()
                .withSchedule(
                    SimpleScheduleBuilder
                        .simpleSchedule()
                        .withIntervalInSeconds(1)
                        .withRepeatCount(0),
                ).build()

        scheduler.scheduleJob(job, trigger)

        // Allow some time for the job to execute
        Thread.sleep(2000)

        // Verify that the service task was performed
        verify(exactly = 1) { rapporteringService.slettMellomlagredeRapporteringsperioder() }
    }
}
