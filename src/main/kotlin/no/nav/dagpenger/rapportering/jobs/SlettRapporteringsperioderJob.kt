package no.nav.dagpenger.rapportering.jobs

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.metrics.JobbkjoringMetrikker
import no.nav.dagpenger.rapportering.model.Leader
import no.nav.dagpenger.rapportering.service.RapporteringService
import java.net.InetAddress
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.measureTime

internal class SlettRapporteringsperioderJob(
    meterRegistry: MeterRegistry,
    private val httpClient: HttpClient,
) {
    private val logger = KotlinLogging.logger { }
    private val metrikker: JobbkjoringMetrikker = JobbkjoringMetrikker(meterRegistry, this::class.simpleName!!)

    private val tidspunktForKjoring = LocalTime.of(0, 50)
    private val naa = ZonedDateTime.now()
    private val tidspunktForNesteKjoring = naa.with(tidspunktForKjoring).plusDays(1)
    private val millisekunderTilNesteKjoring =
        tidspunktForNesteKjoring.toInstant().toEpochMilli() -
            naa.toInstant().toEpochMilli() // differansen i millisekunder mellom de to tidspunktene

    internal fun start(rapporeringService: RapporteringService) {
        logger.info { "Tidspunkt for neste kjøring: $tidspunktForNesteKjoring" }
        fixedRateTimer(
            name = "Slett mellomlagrede rapporteringsperioder",
            daemon = true,
            // Har nåværende tidspunkt passert 'tidspunktForNesteKjoring' starter vi timeren umiddelbart
            initialDelay = millisekunderTilNesteKjoring.coerceAtLeast(0),
            period = 1.days.inWholeMilliseconds,
            action = {
                try {
                    if (!isLeader()) {
                        return@fixedRateTimer
                    }

                    var rowsAffected: Int
                    logger.info { "Starter jobb for å slette mellomlagrede rapporteringsperioder" }
                    val tidBrukt =
                        measureTime {
                            rowsAffected = runBlocking { rapporeringService.slettMellomlagredeRapporteringsperioder() }
                        }
                    logger.info {
                        "Jobb for å slette mellomlagrede rapporteringsperioder ferdig. Brukte ${tidBrukt.inWholeSeconds} sekund(er)."
                    }
                    metrikker.jobbFullfort(tidBrukt, rowsAffected)
                } catch (e: Exception) {
                    logger.warn(e) { "Slettejobb feilet" }
                    metrikker.jobbFeilet()
                }
            },
        )
    }

    private fun isLeader(): Boolean {
        var leader = ""
        val hostname = InetAddress.getLocalHost().hostName

        try {
            val electorUrl = System.getenv("ELECTOR_GET_URL")
            runBlocking {
                val leaderJson: Leader = httpClient.get(electorUrl).body()
                leader = leaderJson.name
            }
        } catch (e: Exception) {
            logger.error(e) { "Kunne ikke sjekke leader" }
            return true // Det er bedre å få flere pod'er til å starte jobben enn ingen
        }

        return hostname == leader
    }
}
