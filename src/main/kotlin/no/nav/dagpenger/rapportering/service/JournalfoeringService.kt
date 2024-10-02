package no.nav.dagpenger.rapportering.service

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.natpryce.konfig.Key
import com.natpryce.konfig.stringType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.contentType
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import mu.KLogging
import no.nav.dagpenger.oauth2.defaultHttpClient
import no.nav.dagpenger.rapportering.config.Configuration
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.config.Configuration.properties
import no.nav.dagpenger.rapportering.metrics.JobbkjoringMetrikker
import no.nav.dagpenger.rapportering.model.MidlertidigLagretData
import no.nav.dagpenger.rapportering.model.MineBehov
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import kotlin.time.measureTime

class JournalfoeringService(
    private val rapidsConnection: RapidsConnection,
    private val journalfoeringRepository: JournalfoeringRepository,
    meterRegistry: MeterRegistry,
    private val httpClient: HttpClient = defaultHttpClient(),
    delay: Long = 10000,
    // 5 minutes by default
    resendInterval: Long = 300_000L,
) {
    companion object : KLogging()

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.YYYY")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.YYYY HH:mm")
    private var locale: Locale? = Locale.of("nb", "NO") // Vi skal regne ukenummer iht norske regler
    private val woy = WeekFields.of(locale).weekOfWeekBasedYear()

    private val metrikker: JobbkjoringMetrikker = JobbkjoringMetrikker(meterRegistry, this::class.simpleName!!)

    init {
        val timer = Timer()
        val timerTask: TimerTask =
            object : TimerTask() {
                override fun run() {
                    runBlocking {
                        try {
                            var rowsAffected: Int
                            val tidBrukt =
                                measureTime {
                                    rowsAffected = journalfoerPaaNytt()
                                }
                            metrikker.jobbFullfort(tidBrukt, rowsAffected)
                        } catch (e: Exception) {
                            metrikker.jobbFeilet()
                            e
                        }
                    }
                }
            }

        timer.schedule(timerTask, delay, resendInterval)
    }

    suspend fun journalfoerPaaNytt(): Int {
        // Les data fra DB
        // Triple: periodeId, MidlertidigLagretData, retries
        val journalpostData: List<Triple<String, MidlertidigLagretData, Int>> =
            journalfoeringRepository.hentMidlertidigLagretData()

        journalpostData.forEach { triple ->
            val periodeId = triple.first
            val midlertidigLagretData = triple.second
            val retries = triple.third

            try {
                // Journalfør
                opprettOgSendBehov(
                    midlertidigLagretData.ident,
                    midlertidigLagretData.navn,
                    midlertidigLagretData.headers,
                    midlertidigLagretData.rapporteringsperiode,
                )

                // Slette midlertidig lagret data
                journalfoeringRepository.sletteMidlertidigLagretData(periodeId)
            } catch (e: Exception) {
                // Kan ikke journalføre igjen. Oppdater teller
                journalfoeringRepository.oppdaterMidlertidigLagretData(periodeId, retries + 1)
                logger.warn(
                    "Kan ikke journalføre periode $periodeId, retries $retries",
                    e,
                )
            }
        }

        return journalpostData.size
    }

    suspend fun journalfoer(
        ident: String,
        navn: String,
        headers: Headers,
        rapporteringsperiode: Rapporteringsperiode,
    ) {
        try {
            opprettOgSendBehov(ident, navn, headers, rapporteringsperiode)
        } catch (e: Exception) {
            logger.warn("Feil ved journalføring", e)
            lagreDataMidlertidig(MidlertidigLagretData(ident, navn, headers, rapporteringsperiode))
        }
    }

    private suspend fun opprettOgSendBehov(
        ident: String,
        navn: String,
        headers: Headers,
        rapporteringsperiode: Rapporteringsperiode,
    ) {
        // Opprett HTML
        // Hent HTML fra frontend
        // TODO: Hent
        val htmlFrafrontend = "<div>!!!</div>"

        // Erstatt plassholdere med data
        val htmlMal = this::class.java.getResource("/html_pdf_mal.html")!!.readText()
        htmlMal.replace("%NAVN%", navn)
        htmlMal.replace("%IDENT%", ident)
        htmlMal.replace("%RAPPORTERINGSPERIODE_ID%", rapporteringsperiode.id.toString())
        htmlMal.replace("%DATO%", LocalDate.now().format(dateFormatter))
        htmlMal.replace("%TITTEL%", getTittle(rapporteringsperiode))
        htmlMal.replace("%MOTTATT%", LocalDateTime.now().format(dateTimeFormatter))
        htmlMal.replace(
            "%NESTE_MELDEKORT_KAN_SENDES_FRA%",
            rapporteringsperiode.kanSendesFra.plusDays(14).format(dateFormatter),
        )
        htmlMal.replace("%HTML%", htmlFrafrontend)

        // TODO: Vi har HTML med alle tekster, vi oppretter PDF fra HTML, må vi også ha alle tekstene i JSON?
        val json = defaultObjectMapper.writeValueAsString(rapporteringsperiode)

        // Kall dp-behov-pdf-generator
        val sak = "meldekort" // Vi bruker "meldekort" istedenfor saksnummer

        logger.info("Oppretter PDF for rapporteringsperiode ${rapporteringsperiode.id}")
        val pdfGeneratorResponse =
            httpClient.post(Configuration.pdfGeneratorUrl + "/convert-html-to-pdf/" + sak) {
                accept(ContentType.Application.Pdf)
                contentType(ContentType.Text.Plain)
                setBody(htmlMal)
            }

        val pdf: ByteArray = pdfGeneratorResponse.body()
        val tilleggsopplysninger = getTilleggsopplysninger(headers, rapporteringsperiode)

        logger.info("Oppretter journalpost for rapporteringsperiode ${rapporteringsperiode.id}")

        var brevkode = "NAV 00-10.02"
        if (rapporteringsperiode.status == RapporteringsperiodeStatus.Endret) {
            brevkode = "NAV 00-10.03"
        }

        val behovNavn = MineBehov.JournalføreRapportering.name
        val behovParams =
            mapOf(
                "periodeId" to rapporteringsperiode.id,
                "brevkode" to brevkode,
                "json" to json,
                "pdf" to pdf,
                "tilleggsopplysninger" to tilleggsopplysninger,
            )

        val behov =
            JsonMessage.newNeed(
                listOf(behovNavn),
                mapOf(
                    "ident" to ident,
                    behovNavn to behovParams,
                ),
            )
        rapidsConnection.publish(ident, behov.toJson())
    }

    private fun getTittle(rapporteringsperiode: Rapporteringsperiode): String {
        val uke1 = rapporteringsperiode.periode.fraOgMed.get(woy)
        val uke2 = rapporteringsperiode.periode.tilOgMed.get(woy)
        val fra = rapporteringsperiode.periode.fraOgMed.format(dateFormatter)
        val til = rapporteringsperiode.periode.tilOgMed.format(dateFormatter)

        var tittel = "Meldekort"
        if (rapporteringsperiode.status == RapporteringsperiodeStatus.Endret) {
            tittel = "Korrigert meldekort"
        }

        return "$tittel for uke $uke1 - $uke2 ($fra - $til) elektronisk mottatt av NAV"
    }

    private fun getTilleggsopplysninger(
        headers: Headers,
        rapporteringsperiode: Rapporteringsperiode,
    ): List<Pair<String, String>> =
        mutableListOf(
            // Nøkkel - maksimum 20 tegn
            // Verdi - maksimum 100 tegn
            Pair(
                "periodeId",
                rapporteringsperiode.id.toString(),
            ),
            Pair(
                "kanSendesFra",
                rapporteringsperiode.kanSendesFra.format(DateTimeFormatter.ISO_DATE),
            ),
            Pair(
                "userAgent",
                headers["useragent"] ?: "",
            ),
            Pair(
                "frontendGithubSha",
                headers["githubsha"] ?: "",
            ),
            Pair(
                "backendGithubSha",
                properties[Key("GITHUB_SHA", stringType)],
            ),
        )

    private suspend fun lagreDataMidlertidig(midlertidigLagretData: MidlertidigLagretData) {
        logger.info("Mellomlagrer data for rapporteringsperiode ${midlertidigLagretData.rapporteringsperiode.id}")
        journalfoeringRepository.lagreDataMidlertidig(midlertidigLagretData)
    }
}
