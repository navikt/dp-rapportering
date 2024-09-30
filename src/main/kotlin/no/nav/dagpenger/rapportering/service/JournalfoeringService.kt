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
import mu.KLogging
import no.nav.dagpenger.oauth2.defaultHttpClient
import no.nav.dagpenger.rapportering.config.Configuration
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.config.Configuration.properties
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.metrics.JobbkjoringMetrikker
import no.nav.dagpenger.rapportering.model.MineBehov
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

class JournalfoeringService(
    private val meldepliktConnector: MeldepliktConnector,
    private val rapidsConnection: RapidsConnection,
    private val journalfoeringRepository: JournalfoeringRepository,
    meterRegistry: MeterRegistry,
    private val httpClient: HttpClient = defaultHttpClient(),
) {
    companion object : KLogging()

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.YYYY")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.YYYY HH:mm")
    private var locale: Locale? = Locale.of("nb", "NO") // Vi skal regne ukenummer iht norske regler
    private val woy = WeekFields.of(locale).weekOfWeekBasedYear()

    private val metrikker: JobbkjoringMetrikker = JobbkjoringMetrikker(meterRegistry, this::class.simpleName!!)

    /*
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
                                    rowsAffected = sendJournalposterPaaNytt()
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
    */

    suspend fun sendJournalposterPaaNytt(): Int {
        /*
        // Les data fra DB
        // Triple: data id, journalpost, retries
        val journalpostData: List<Triple<String, Journalpost, Int>> =
            journalfoeringRepository.hentMidlertidigLagredeJournalposter()

        journalpostData.forEach { triple ->
            val lagretJournalpostId = triple.first
            val journalpost = triple.second
            val retries = triple.third

            try {
                // Det er mulig at vi får feil et sted her, dvs. f.eks. når vi lagrer informasjon om at en journalpost har blitt opprettet
                // (noe med DB eller connection timeout før vi får JournalpostRepspose tilbake)
                // Da prøver på nytt. Men journalposten eksisterer allerede, vi bare vet ikke om dette
                // Hva skjer hvis vi prøver å opprette journalpost som allerede eksisterer? No stress.
                // Hvis journalpost med denne eksternReferanseId allerede eksisterer, returnerer createJournalpost 409 Conflict
                // Men! Sammen men 409 Conflict returneres vanlig JournalpostReponse
                // Dvs. vi kan lagre journalpostId og dokumentInfoId og slette midlertidig lagret journalpost fra DB

                // Send
                val journalpostResponse = dokarkivConnector.sendJournalpost(journalpost)
                val journalpostId = journalpostResponse.journalpostId
                val dokumentInfoId = journalpostResponse.dokumenter[0].dokumentInfoId
                val rapporteringsperiodeId =
                    journalpost.tilleggsopplysninger!!
                        .first { it.nokkel == "id" }
                        .verdi
                        .toLong()

                val lagretJournalpostData = journalfoeringRepository.hentJournalpostData(journalpostId)

                if (lagretJournalpostData.isEmpty()) {
                    // Lagre journalpostId-meldekortId
                    journalfoeringRepository.lagreJournalpostData(
                        journalpostId,
                        dokumentInfoId,
                        rapporteringsperiodeId,
                    )

                    // Slette midlertidig lagret journalpost
                    journalfoeringRepository.sletteMidlertidigLagretJournalpost(lagretJournalpostId)
                } else {
                    val lagretJournalpost = lagretJournalpostData[0]

                    if (lagretJournalpost.second == dokumentInfoId && lagretJournalpost.third == rapporteringsperiodeId) {
                        // Slette midlertidig lagret journalpost
                        journalfoeringRepository.sletteMidlertidigLagretJournalpost(lagretJournalpostId)
                    } else {
                        logger.error(
                            "Journalpost med ID $journalpostId eksisterer allerede, " +
                                "men har uforventet dokumentInfoId og rapporteringsperiodeId",
                        )
                    }
                }
            } catch (e: Exception) {
                // Kan ikke sende journalpost igjen. Oppdater teller
                journalfoeringRepository.oppdaterMidlertidigLagretJournalpost(lagretJournalpostId, retries + 1)
                logger.warn(
                    "Kan ikke opprette journalpost igjen. Data ID = $lagretJournalpostId, retries = $retries",
                    e,
                )
            }
        }

        return journalpostData.size
        */
        return 0
    }

    suspend fun journalfoer(
        ident: String,
        token: String,
        headers: Headers,
        rapporteringsperiode: Rapporteringsperiode,
    ) {
        try {
            val person = meldepliktConnector.hentPerson(ident, token)
            val navn = person?.fornavn + " " + person?.etternavn

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
                rapporteringsperiode.kanSendesFra.plusDays(14).format(dateFormatter)
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

            val behov =  JsonMessage.newNeed(
                listOf(MineBehov.JournalføreRapportering.name),
                mapOf(
                    "periodeId" to rapporteringsperiode.id,
                    "brevkode" to brevkode,
                    "json" to json,
                    "pdf" to pdf,
                    "tilleggsopplysninger" to tilleggsopplysninger
                )
            )
            rapidsConnection.publish(ident, behov.toJson())

        } catch (e: Exception) {
            logger.warn("Kan ikke sende journalpost", e)

            lagreJournalpostMidlertidig(rapporteringsperiode)
        }

        /*
                    AvsenderMottaker(
                        id = ident,
                        idType = AvsenderIdType.FNR,
                        navn = navn, // Finnes ikke dp-behov-journalføring
                    ),
                tittel = getTittle(rapporteringsperiode), // Finnes ikke dp-behov-journalføring
        */
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
                rapporteringsperiode.id.toString()
            ),
            Pair(
                "kanSendesFra",
                rapporteringsperiode.kanSendesFra.format(DateTimeFormatter.ISO_DATE)
            ),
            Pair(
                "userAgent",
                headers["useragent"] ?: ""
            ),
            Pair(
                "frontendGithubSha",
                headers["githubsha"] ?: ""
            ),
            Pair(
                "backendGithubSha",
                properties[Key("GITHUB_SHA", stringType)]
            ),
        )

    private fun lagreJournalpostMidlertidig(rapporteringsperiode: Rapporteringsperiode) {
        logger.info("Mellomlagrer journalpost for rapporteringsperiode ${rapporteringsperiode.id}")
        journalfoeringRepository.lagreJournalpostMidlertidig(rapporteringsperiode)
    }
}
