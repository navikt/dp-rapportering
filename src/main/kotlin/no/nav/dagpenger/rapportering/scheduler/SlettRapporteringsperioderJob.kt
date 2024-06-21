package no.nav.dagpenger.rapportering.scheduler

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.service.RapporteringService
import org.quartz.Job
import org.quartz.JobExecutionContext
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {}

class SlettRapporteringsperioderJob : Job {
    override fun execute(context: JobExecutionContext) {
        logger.info { "Starter jobb for å slette mellomlagrede Rapporteringsperioder" }
        try {
            val rapporteringService = context.jobDetail.jobDataMap["rapporteringService"] as RapporteringService
            val tidIMs =
                measureTimeMillis {
                    rapporteringService.slettMellomlagredeRapporteringsperioder()
                }
            logger.info { "Jobb for å slette mellomlagrede Rapporteringsperioder ferdig. Brukte ${tidIMs / 1000} sekund(er)." }
        } catch (e: Exception) {
            logger.warn(e) { "Slettejobb feilet" }
        }
    }
}
