package no.nav.dagpenger.rapportering.service

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.natpryce.konfig.Key
import com.natpryce.konfig.stringType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HeadersImpl
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.toMap
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.ApplicationBuilder.Companion.getRapidsConnection
import no.nav.dagpenger.rapportering.config.Configuration
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.config.Configuration.properties
import no.nav.dagpenger.rapportering.model.Leader
import no.nav.dagpenger.rapportering.model.MidlertidigLagretData
import no.nav.dagpenger.rapportering.model.MineBehov
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.erEndring
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepository
import java.net.InetAddress
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

class JournalfoeringService(
    private val journalfoeringRepository: JournalfoeringRepository,
    private val kallLoggService: KallLoggService,
    private val httpClient: HttpClient,
) {
    private val logger = KotlinLogging.logger {}

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    private var locale: Locale? = Locale.of("nb", "NO") // Vi skal regne ukenummer iht norske regler
    private val woy = WeekFields.of(locale).weekOfWeekBasedYear()

    suspend fun journalfoerPaaNytt(): Int {
        // Les data fra DB
        // Triple: periodeId, MidlertidigLagretData, retries
        val journalpostData: List<Triple<String, MidlertidigLagretData, Int>> =
            journalfoeringRepository.hentMidlertidigLagretData()

        journalpostData.forEach { triple ->
            val id = triple.first
            val midlertidigLagretData = triple.second
            val retries = triple.third

            val rapporteringsperiode = defaultObjectMapper.readValue<Rapporteringsperiode>(midlertidigLagretData.rapporteringsperiode)

            try {
                // Journalfør
                opprettOgSendBehov(
                    midlertidigLagretData.ident,
                    midlertidigLagretData.navn,
                    midlertidigLagretData.loginLevel,
                    HeadersImpl(midlertidigLagretData.headers),
                    rapporteringsperiode,
                )

                // Slette midlertidig lagret data
                journalfoeringRepository.sletteMidlertidigLagretData(id)
            } catch (e: Exception) {
                // Kan ikke journalføre igjen. Oppdater teller
                journalfoeringRepository.oppdaterMidlertidigLagretData(id, retries + 1)
                logger.warn(
                    "Kan ikke journalføre periode ${rapporteringsperiode.id}, retries $retries",
                    e,
                )
            }
        }

        return journalpostData.size
    }

    suspend fun journalfoer(
        ident: String,
        navn: String,
        loginLevel: Int,
        headers: Headers,
        rapporteringsperiode: Rapporteringsperiode,
    ) {
        try {
            opprettOgSendBehov(ident, navn, loginLevel, headers, rapporteringsperiode)
        } catch (e: Exception) {
            logger.warn("Feil ved journalføring", e)
            lagreDataMidlertidig(ident, navn, loginLevel, headers, rapporteringsperiode)
        }
    }

    private suspend fun opprettOgSendBehov(
        ident: String,
        navn: String,
        loginLevel: Int,
        headers: Headers,
        rapporteringsperiode: Rapporteringsperiode,
    ) {
        // Opprett HTML
        // Erstatt plassholdere med data
        val htmlMal =
            this::class.java
                .getResource("/html_pdf_mal.html")!!
                .readText()
                .replace("%NAVN%", navn)
                .replace("%IDENT%", ident)
                .replace("%RAPPORTERINGSPERIODE_ID%", rapporteringsperiode.id.toString())
                .replace("%TITTEL%", getTittle(rapporteringsperiode))
                .replace("%MOTTATT%", LocalDateTime.now().format(dateTimeFormatter))
                .replace(
                    "%NESTE_MELDEKORT_KAN_SENDES_FRA%",
                    rapporteringsperiode.periode.tilOgMed
                        .plusDays(13)
                        .format(dateFormatter),
                ).replace("%HTML%", rapporteringsperiode.html ?: "")

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

        if (pdfGeneratorResponse.status != HttpStatusCode.OK) {
            throw Exception("Kunne ikke generere PDF. Fikk status ${pdfGeneratorResponse.status}")
        }

        val pdf: ByteArray = pdfGeneratorResponse.body()

        logger.info("Oppretter journalpost for rapporteringsperiode ${rapporteringsperiode.id}")

        var brevkode = "NAV 00-10.02"
        if (rapporteringsperiode.erEndring()) {
            brevkode = "NAV 00-10.03"
        }

        val tilleggsopplysninger = getTilleggsopplysninger(loginLevel, headers, rapporteringsperiode)

        // Oppretter kallLogg for å få kallLoggId slik at vi kan sende den med Behov
        val kallLoggId = kallLoggService.lagreKafkaUtKallLogg(ident)

        val behovNavn = MineBehov.JournalføreRapportering.name
        val behovParams =
            mapOf(
                "periodeId" to rapporteringsperiode.id,
                "brevkode" to brevkode,
                "tittel" to getTittle(rapporteringsperiode),
                "json" to json,
                "pdf" to pdf,
                "tilleggsopplysninger" to tilleggsopplysninger,
                "kallLoggId" to kallLoggId,
            )

        val behov =
            JsonMessage.newNeed(
                listOf(behovNavn),
                mapOf(
                    "ident" to ident,
                    behovNavn to behovParams,
                ),
            )

        try {
            getRapidsConnection().publish(ident, behov.toJson())

            // Lagrer request (Behov)
            kallLoggService.lagreRequest(kallLoggId, behov.toJson())
        } catch (e: Exception) {
            logger.error("Kunne ikke sende melding til Kafka", e)

            kallLoggService.lagreResponse(kallLoggId, 500, "")

            throw Exception(e)
        }
    }

    private fun getTittle(rapporteringsperiode: Rapporteringsperiode): String {
        val uke1 = rapporteringsperiode.periode.fraOgMed.get(woy)
        val uke2 = rapporteringsperiode.periode.tilOgMed.get(woy)
        val fra = rapporteringsperiode.periode.fraOgMed.format(dateFormatter)
        val til = rapporteringsperiode.periode.tilOgMed.format(dateFormatter)

        var tittel = "Meldekort"
        if (rapporteringsperiode.erEndring()) {
            tittel = "Korrigert meldekort"
        }

        return "$tittel for uke $uke1 - $uke2 ($fra - $til) elektronisk mottatt av Nav"
    }

    private fun getTilleggsopplysninger(
        loginLevel: Int,
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
            Pair(
                "loginLevel",
                loginLevel.toString(),
            ),
        )

    private suspend fun lagreDataMidlertidig(
        ident: String,
        navn: String,
        loginLevel: Int,
        headers: Headers,
        rapporteringsperiode: Rapporteringsperiode,
    ) {
        logger.info("Mellomlagrer data for rapporteringsperiode ${rapporteringsperiode.id}")
        journalfoeringRepository.lagreDataMidlertidig(
            MidlertidigLagretData(
                ident,
                navn,
                loginLevel,
                headers.toMap(),
                defaultObjectMapper.writeValueAsString(rapporteringsperiode),
            ),
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
