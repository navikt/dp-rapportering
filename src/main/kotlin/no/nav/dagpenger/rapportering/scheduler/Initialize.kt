package no.nav.dagpenger.rapportering.scheduler

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import no.nav.dagpenger.rapportering.service.RapporteringService
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.JobDataMap
import org.quartz.TriggerBuilder
import org.quartz.impl.StdSchedulerFactory

fun Application.initializeQuartz(rapporteringService: RapporteringService) {
    val slettPerioderJobb =
        JobBuilder
            .newJob(SlettRapporteringsperioderJob::class.java)
            .withIdentity("slett_perioder_jobb")
            .setJobData(
                JobDataMap().apply {
                    put("rapporteringService", rapporteringService)
                },
            ).build()

    val slettPerioderTrigger =
        TriggerBuilder
            .newTrigger()
            .withIdentity("slett_perioder_trigger")
            .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(0, 0))
            .build()

    val scheduler = StdSchedulerFactory().scheduler
    scheduler.start()
    scheduler.scheduleJob(slettPerioderJobb, slettPerioderTrigger)

    environment.monitor.subscribe(ApplicationStopped) {
        scheduler.shutdown()
    }
}
