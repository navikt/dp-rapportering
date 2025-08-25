package no.nav.dagpenger.rapportering.jobs

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.metrics.JobbkjoringMetrikker
import no.nav.dagpenger.rapportering.service.JournalfoeringService
import java.util.Timer
import java.util.TimerTask
import kotlin.time.measureTime

internal class MidlertidigJournalføringJob(
    private val httpClient: HttpClient,
    meterRegistry: MeterRegistry,
    private val delay: Long = 10000,
    // 5 minutes by default
    private val resendInterval: Long = 300_000L,
) {
    private val logger = KotlinLogging.logger {}
    private val metrikker: JobbkjoringMetrikker = JobbkjoringMetrikker(meterRegistry, this::class.simpleName!!)

    internal fun start(journalføringService: JournalfoeringService) {
        val timer = Timer()
        val timerTask: TimerTask =
            object : TimerTask() {
                override fun run() {
                    try {
                        if (isLeader(httpClient, logger)) {
                            logger.info { "Pod er leader. Starter jobb for å sende journalposter på nytt" }
                            var rowsAffected: Int
                            val tidBrukt =
                                measureTime {
                                    rowsAffected = runBlocking { journalføringService.journalfoerPaaNytt() }
                                }
                            metrikker.jobbFullfort(tidBrukt, rowsAffected)
                        }
                    } catch (e: Exception) {
                        logger.warn(e) { "JournalfoerPaaNytt feilet" }
                        metrikker.jobbFeilet()
                    }
                }
            }

        timer.schedule(timerTask, delay, resendInterval)
    }
}
