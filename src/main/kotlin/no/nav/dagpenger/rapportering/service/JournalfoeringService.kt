package no.nav.dagpenger.rapportering.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.jetty.Jetty
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import mu.KLogging
import no.nav.dagpenger.rapportering.Configuration
import no.nav.dagpenger.rapportering.connector.createHttpClient
import no.nav.dagpenger.rapportering.model.AvsenderIdType
import no.nav.dagpenger.rapportering.model.AvsenderMottaker
import no.nav.dagpenger.rapportering.model.Bruker
import no.nav.dagpenger.rapportering.model.BrukerIdType
import no.nav.dagpenger.rapportering.model.Dokument
import no.nav.dagpenger.rapportering.model.DokumentVariant
import no.nav.dagpenger.rapportering.model.Filetype
import no.nav.dagpenger.rapportering.model.Journalpost
import no.nav.dagpenger.rapportering.model.JournalpostResponse
import no.nav.dagpenger.rapportering.model.Journalposttype
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.Sak
import no.nav.dagpenger.rapportering.model.Sakstype
import no.nav.dagpenger.rapportering.model.Tema
import no.nav.dagpenger.rapportering.model.Tilleggsopplysning
import no.nav.dagpenger.rapportering.model.Variantformat
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepository
import no.nav.dagpenger.rapportering.utils.PDFGenerator
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Base64
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

class JournalfoeringService(
    private val journalfoeringRepository: JournalfoeringRepository,
    private val dokarkivUrl: String = Configuration.dokarkivUrl,
    private val tokenProvider: (String) -> String = Configuration.azureADClient(),
    engine: HttpClientEngine = Jetty.create { },
) {
    companion object : KLogging()

    private var resendInterval = 300_000L // 5 minutes by default

    private val kanal = "NAV_NO"
    private val journalfoerendeEnhet = "9999"
    private val brevkode = "NAV 00-10.02"
    private val brevkodeKorrigert = "NAV 00-10.03"

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.YYYY")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.YYYY HH:mm")
    private var locale: Locale? = Locale.of("nb", "NO") // Vi skal regne ukenummer iht norske regler
    private val woy = WeekFields.of(locale).weekOfWeekBasedYear()

    private val path = "/rest/journalpostapi/v1/journalpost"

    private val httpClient = createHttpClient(engine)

    init {
        val timer = Timer()
        val timerTask: TimerTask =
            object : TimerTask() {
                override fun run() {
                    sendJournalposterPaaNytt()
                }
            }

        timer.schedule(timerTask, 10000, resendInterval)
    }

    fun sendJournalposterPaaNytt() {
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
                val journalpostResponse = sendJournalpost(journalpost)
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
    }

    suspend fun journalfoer(
        ident: String,
        loginLevel: Int,
        rapporteringsperiode: Rapporteringsperiode,
    ) {
        // TODO: Hvordan kan vi hente navn?
        val navn = "NAVN"

        val journalpost =
            Journalpost(
                journalposttype = Journalposttype.INNGAAENDE,
                avsenderMottaker =
                    AvsenderMottaker(
                        id = ident,
                        idType = AvsenderIdType.FNR,
                        navn = navn,
                    ),
                bruker =
                    Bruker(
                        id = ident,
                        idType = BrukerIdType.FNR,
                    ),
                tema = Tema.DAG,
                tittel = getTittle(rapporteringsperiode),
                kanal = kanal,
                journalfoerendeEnhet = journalfoerendeEnhet,
                // Det er duplikatkontroll på eksternReferanseId for inngående dokumenter
                eksternReferanseId = UUID.randomUUID().toString(),
                datoMottatt = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
                tilleggsopplysninger = getTilleggsopplysninger(rapporteringsperiode),
                sak =
                    Sak(
                        sakstype = Sakstype.GENERELL_SAK,
                    ),
                dokumenter = getDokumenter(rapporteringsperiode, ident, navn, loginLevel),
            )

        logger.info("Opprettet journalpost for rapporteringsperiode ${rapporteringsperiode.id}")

        try {
            val journalpostResponse = sendJournalpost(journalpost)

            lagreJournalpostData(
                journalpostResponse.journalpostId,
                journalpostResponse.dokumenter[0].dokumentInfoId,
                rapporteringsperiode.id,
            )
        } catch (e: Exception) {
            logger.warn("Kan ikke sende journalpost", e)

            lagreJournalpostMidlertidig(rapporteringsperiode.id, journalpost)
        }
    }

    private fun sendJournalpost(journalpost: Journalpost): JournalpostResponse {
        var jp: JournalpostResponse

        runBlocking {
            val token = tokenProvider.invoke("api://${Configuration.dokarkivAudience}/.default")

            logger.info("Prøver å sende journalpost " + journalpost.eksternReferanseId)
            logger.info("URL: $dokarkivUrl$path")

            val response =
                httpClient
                    .post(URI("$dokarkivUrl$path").toURL()) {
                        bearerAuth(token)
                        accept(ContentType.Application.Json)
                        contentType(ContentType.Application.Json)
                        setBody(journalpost)
                    }

            logger.info("Journalpost sendt. Svar " + response.status)
            jp = response.body<JournalpostResponse>()
        }

        return jp
    }

    private fun getTittle(rapporteringsperiode: Rapporteringsperiode): String {
        val uke1 = rapporteringsperiode.periode.fraOgMed.get(woy)
        val uke2 = rapporteringsperiode.periode.tilOgMed.get(woy)
        val fra = rapporteringsperiode.periode.fraOgMed.format(dateFormatter)
        val til = rapporteringsperiode.periode.tilOgMed.format(dateFormatter)

        var tittel = "Meldekort"
        if (rapporteringsperiode.status == RapporteringsperiodeStatus.Korrigert) {
            tittel = "Korrigert meldekort"
        }

        return "$tittel for uke $uke1 - $uke2 ($fra - $til) elektronisk mottatt av NAV"
    }

    private fun getTilleggsopplysninger(rapporteringsperiode: Rapporteringsperiode): List<Tilleggsopplysning> =
        mutableListOf(
            // Nøkkel - maksimum 20 tegn
            Tilleggsopplysning(
                "id",
                rapporteringsperiode.id.toString(),
            ),
            Tilleggsopplysning(
                "kanSendesFra",
                rapporteringsperiode.kanSendesFra.format(DateTimeFormatter.ISO_DATE),
            ),
        )

    private fun getDokumenter(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
        navn: String,
        loginLevel: Int,
    ): List<Dokument> {
        var brevkode = brevkode
        if (rapporteringsperiode.status == RapporteringsperiodeStatus.Korrigert) {
            brevkode = brevkodeKorrigert
        }

        val dokument =
            Dokument(
                tittel = getTittle(rapporteringsperiode),
                brevkode = brevkode,
                dokumentvarianter =
                    listOf(
                        getJSON(rapporteringsperiode),
                        getPDF(rapporteringsperiode, ident, navn, loginLevel),
                    ),
            )

        return listOf(dokument)
    }

    private fun getJSON(rapporteringsperiode: Rapporteringsperiode): DokumentVariant {
        val objectMapper =
            ObjectMapper()
                .registerKotlinModule()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        return DokumentVariant(
            filtype = Filetype.JSON,
            variantformat = Variantformat.ORIGINAL,
            fysiskDokument = Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(rapporteringsperiode)),
        )
    }

    private fun getPDF(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
        navn: String,
        loginLevel: Int,
    ): DokumentVariant {
        var tittel = "Elektronisk innsendt meldekort"
        if (rapporteringsperiode.status == RapporteringsperiodeStatus.Korrigert) {
            tittel = "Elektronisk korrigert meldekort"
        }

        val logo = this::class.java.getResource("/nav-logo.svg")!!.readText()

        val aktiviteter =
            rapporteringsperiode.dager.joinToString("\n") { dag ->
                "<div>" +
                    "<b>" + dag.dato.format(dateFormatter) + ":</b> " +
                    dag.aktiviteter.joinToString(", ") { aktivitet ->
                        val timer =
                            if (!aktivitet.timer.isNullOrBlank() && aktivitet.timer.toDouble() > 0) {
                                " " + aktivitet.timer + "t"
                            } else {
                                ""
                            }

                        "" + aktivitet.type + timer
                    } +
                    "</div>"
            }

        val html =
            """
                <div class="info">
                   <b>ID:</b> ${rapporteringsperiode.id}<br/>
                   <b>Tema:</b> ${Tema.DAG.tittel}<br/>
                   <b>Tilgangsnivå:</b> $loginLevel
                </div>
                
                $logo
                
                <h1>$tittel</h1>
                <div><b>${getTittle(rapporteringsperiode)}</b></div>
                <div><b>Meldekortet ble mottatt:</b> ${LocalDateTime.now().format(dateTimeFormatter)}</div>
                <div><b>Bruker:</b> $navn ($ident)</div>
                <div><b>Neste meldekort kan sendes inn fra:</b> ${rapporteringsperiode.kanSendesFra.format(dateFormatter)}</div>
                <br>
                $aktiviteter
                """

        val pdf = PDFGenerator().createPDFA(html)

        return DokumentVariant(
            filtype = Filetype.PDFA,
            variantformat = Variantformat.ARKIV,
            fysiskDokument = Base64.getEncoder().encodeToString(pdf),
        )
    }

    private fun lagreJournalpostData(
        journalpostId: Long,
        dokumentInfoId: Long,
        rapporteringsperiodeId: Long,
    ) {
        logger.info("Lagrer JournalpostData for rapporteringsperiode $rapporteringsperiodeId")
        journalfoeringRepository.lagreJournalpostData(journalpostId, dokumentInfoId, rapporteringsperiodeId)
    }

    private fun lagreJournalpostMidlertidig(
        rapporteringsperiodeId: Long,
        journalpost: Journalpost,
    ) {
        logger.info("Mellomlagrer journalpost for rapporteringsperiode $rapporteringsperiodeId")
        journalfoeringRepository.lagreJournalpostMidlertidig(journalpost)
    }
}
