package no.nav.dagpenger.rapportering.jobs

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.opentelemetry.api.GlobalOpenTelemetry
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.metrics.JobbkjøringMetrikker
import no.nav.dagpenger.rapportering.service.JournalfoeringService
import java.util.Timer
import java.util.TimerTask
import kotlin.jvm.java
import kotlin.time.measureTime
import kotlin.use

internal class MidlertidigJournalføringJob(
    private val httpClient: HttpClient,
    private val delay: Long = 10000,
    private val resendInterval: Long = 300_000L,
    private val jobbkjøringMetrikker: JobbkjøringMetrikker,
) {
    private val logger = KotlinLogging.logger {}

    internal fun start(journalføringService: JournalfoeringService) {
        val timer = Timer()
        val timerTask: TimerTask =
            object : TimerTask() {
                override fun run() {
                    val span = tracer.spanBuilder("midlertidig-journalføring").startSpan()
                    try {
                        span.makeCurrent().use {
                            if (isLeader(httpClient, logger)) {
                                logger.info { "Pod er leader. Starter jobb for å sende journalposter på nytt" }
                                var rowsAffected: Int
                                val tidBrukt =
                                    measureTime {
                                        rowsAffected = runBlocking { journalføringService.journalfoerPaaNytt() }
                                    }
                                jobbkjøringMetrikker.jobbFullfort(tidBrukt, rowsAffected)
                            }
                        }
                    } catch (e: Exception) {
                        span.recordException(e)
                        logger.error(e) { "JournalfoerPaaNytt feilet" }
                        jobbkjøringMetrikker.jobbFeilet()
                    } finally {
                        jobbkjøringMetrikker.jobbSjekketOmDenSkulleKjøre()
                        span.end()
                    }
                }
            }

        timer.schedule(timerTask, delay, resendInterval)
    }

    private companion object {
        private val tracer = GlobalOpenTelemetry.getTracer(MidlertidigJournalføringJob::class.java.name)
    }
}
