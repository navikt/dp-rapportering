package no.nav.dagpenger.rapportering.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import de.redsix.pdfcompare.CompareResultImpl
import de.redsix.pdfcompare.PageArea
import de.redsix.pdfcompare.PdfComparator
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.util.toByteArray
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.connector.DokarkivConnector
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.connector.createHttpClient
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.AvsenderIdType
import no.nav.dagpenger.rapportering.model.BrukerIdType
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Filetype
import no.nav.dagpenger.rapportering.model.Journalpost
import no.nav.dagpenger.rapportering.model.Journalposttype
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Person
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.model.Sakstype
import no.nav.dagpenger.rapportering.model.Tema
import no.nav.dagpenger.rapportering.model.Tilleggsopplysning
import no.nav.dagpenger.rapportering.model.Variantformat
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepository
import no.nav.dagpenger.rapportering.repository.Postgres.database
import no.nav.dagpenger.rapportering.utils.MetricsTestUtil.actionTimer
import no.nav.dagpenger.rapportering.utils.MetricsTestUtil.meterRegistry
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

class JournalfoeringServiceTest {
    private val dokarkivUrl = "https://dokarkiv.nav.no"
    private val githubSha = "abcdwfg"
    private val token = "jwtToken"
    private val headers =
        Headers.build {
            append("useragent", "Some agent")
            append("githubsha", "Some image")
        }

    private val objectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Test
    fun `Kan opprette og sende journalpost`() {
        test()
    }

    @Test
    fun `Kan opprette og sende journalpost ved endring`() {
        test(true)
    }

    @Test
    fun `Kan opprette og sende journalpost med html`() {
        test(false, "<div>Test</div>")
    }

    @Test
    fun `Kan lagre journalposter midlertidig ved feil og sende paa nytt`() {
        setProperties()

        // Mock TokenProvider
        fun mockTokenProvider() =
            { _: String ->
                "token"
            }

        // Mock svar fra Dokarkiv
        var count = 0
        val mockEngine =
            MockEngine { _ ->
                if (count < 2) {
                    count++

                    respond(
                        content = "",
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    respond(
                        content =
                            ByteReadChannel(
                                """
                                    {
                                        "journalpostId": 2,
                                        "journalstatus": "OK",
                                        "journalpostferdigstilt": true,
                                        "dokumenter": [
                                            {
                                                "dokumentInfoId": 3
                                            }
                                        ]
                                    }
                                """.trimMargin(),
                            ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }

        // Mock
        val meldepliktConnector = mockk<MeldepliktConnector>()
        coEvery { meldepliktConnector.hentPerson(any(), any()) } returns Person(1L, "TESTESSEN", "TEST", "NO", "EMELD")

        val dokarkivConnector = DokarkivConnector(dokarkivUrl, mockTokenProvider(), createHttpClient(mockEngine), actionTimer)

        val journalfoeringRepository = mockk<JournalfoeringRepository>()
        every { journalfoeringRepository.lagreJournalpostMidlertidig(any()) } just runs
        every { journalfoeringRepository.hentJournalpostData(any()) } returns emptyList()
        every { journalfoeringRepository.lagreJournalpostData(any(), any(), any()) } just runs
        every { journalfoeringRepository.oppdaterMidlertidigLagretJournalpost(any(), any()) } just runs
        every { journalfoeringRepository.sletteMidlertidigLagretJournalpost(any()) } just runs
        every { journalfoeringRepository.hentMidlertidigLagredeJournalposter() } returns
            listOf(
                Triple(
                    "1",
                    Journalpost(
                        Journalposttype.INNGAAENDE,
                        tilleggsopplysninger = listOf(Tilleggsopplysning("id", "2")),
                    ),
                    0,
                ),
            )

        val journalfoeringService =
            JournalfoeringService(
                meldepliktConnector,
                dokarkivConnector,
                journalfoeringRepository,
                meterRegistry,
                200000,
                200000,
            )

        // Oppretter rapporteringsperiode
        val rapporteringsperiode = createRapporteringsperiode(false)

        // Prøver å sende
        runBlocking {
            journalfoeringService.journalfoer("01020312345", 0, token, headers, rapporteringsperiode)
        }

        // Får feil og sjekker at JournalfoeringService lagrer journalpost midlertidig
        verify { journalfoeringRepository.lagreJournalpostMidlertidig(any()) }

        runBlocking {
            journalfoeringService.sendJournalposterPaaNytt()
        }

        // Sjekker at JournalfoeringService prøvde å sende journalpost på nytt, fikk feil og oppdaterte retries
        verify { journalfoeringRepository.hentMidlertidigLagredeJournalposter() }
        verify { journalfoeringRepository.oppdaterMidlertidigLagretJournalpost("1", 1) }

        runBlocking {
            journalfoeringService.sendJournalposterPaaNytt()
        }

        verify { journalfoeringRepository.hentJournalpostData(2) }
        verify { journalfoeringRepository.sletteMidlertidigLagretJournalpost("1") }
    }

    private fun setProperties() {
        System.setProperty(
            "DB_JDBC_URL",
            "${database.jdbcUrl}&user=${database.username}&password=${database.password}",
        )
        System.setProperty("DOKARKIV_HOST", dokarkivUrl)
        System.setProperty("DOKARKIV_AUDIENCE", "test.test.dokarkiv")
        System.setProperty("AZURE_APP_WELL_KNOWN_URL", "test.test.dokarkiv")
        System.setProperty("GITHUB_SHA", githubSha)
    }

    private fun test(
        endring: Boolean = false,
        html: String? = null,
    ) {
        setProperties()

        // Mock TokenProvider
        fun mockTokenProvider() =
            { _: String ->
                "token"
            }

        // Mock
        val meldepliktConnector = mockk<MeldepliktConnector>()
        coEvery { meldepliktConnector.hentPerson(any(), any()) } returns Person(1L, "TESTESSEN", "TEST", "NO", "EMELD")

        val journalfoeringRepository = mockk<JournalfoeringRepository>()
        justRun { journalfoeringRepository.lagreJournalpostData(eq(2), eq(3), eq(1)) }
        every { journalfoeringRepository.hentMidlertidigLagredeJournalposter() } returns emptyList()

        // Mock svar fra Dokarkiv
        val mockEngine =
            MockEngine { _ ->
                respond(
                    content =
                        ByteReadChannel(
                            """
                                {
                                    "journalpostId": 2,
                                    "journalstatus": "OK",
                                    "journalpostferdigstilt": true,
                                    "dokumenter": [
                                        {
                                            "dokumentInfoId": 3
                                        }
                                    ]
                                }
                            """.trimMargin(),
                        ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }

        val dokarkivConnector = DokarkivConnector(dokarkivUrl, mockTokenProvider(), createHttpClient(mockEngine), actionTimer)

        val journalfoeringService =
            JournalfoeringService(
                meldepliktConnector,
                dokarkivConnector,
                journalfoeringRepository,
                meterRegistry,
            )

        val rapporteringsperiode = createRapporteringsperiode(endring, html)

        // Kjører
        runBlocking {
            journalfoeringService.journalfoer("01020312345", 0, token, headers, rapporteringsperiode)
        }

        // Sjekker
        mockEngine.requestHistory.size shouldBe 1
        mockEngine.responseHistory.size shouldBe 1

        runBlocking {
            checkJournalpost(endring, mockEngine.requestHistory[0].body, rapporteringsperiode, html != null)
        }
    }

    private fun createRapporteringsperiode(
        endring: Boolean,
        html: String? = null,
    ): Rapporteringsperiode {
        val fom = LocalDate.of(2024, 6, 24)

        return Rapporteringsperiode(
            id = 1L,
            periode = Periode(fom, fom.plusDays(13)),
            dager =
                listOf(
                    Dag(
                        fom,
                        listOf(
                            Aktivitet(
                                UUID.randomUUID(),
                                Aktivitet.AktivitetsType.Arbeid,
                                "PT07H30M",
                            ),
                        ),
                        1,
                    ),
                    Dag(
                        fom.plusDays(1),
                        listOf(
                            Aktivitet(
                                UUID.randomUUID(),
                                Aktivitet.AktivitetsType.Syk,
                                null,
                            ),
                        ),
                        2,
                    ),
                ),
            kanSendesFra = fom.plusDays(12),
            sisteFristForTrekk = fom.plusDays(20),
            kanSendes = true,
            kanEndres = true,
            bruttoBelop = 0.0,
            begrunnelseEndring = null,
            status = if (endring) RapporteringsperiodeStatus.Endret else TilUtfylling,
            mottattDato = LocalDate.now(),
            registrertArbeidssoker = true,
            originalId = null,
            rapporteringstype = null,
            html = html,
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun checkJournalpost(
        endring: Boolean,
        content: OutgoingContent,
        opprineligRapporteringsperiode: Rapporteringsperiode,
        withHtml: Boolean = false,
    ) {
        // Henter Journalpost vi har sendt
        val bodyString =
            when (content) {
                is OutgoingContent.WriteChannelContent -> {
                    val channel = GlobalScope.writer(Dispatchers.IO) { content.writeTo(channel) }.channel
                    String(channel.toByteArray())
                }

                is TextContent -> content.text
                else -> throw IllegalArgumentException("Unsupported content type")
            }

        val journalpost = objectMapper.readValue(bodyString, Journalpost::class.java)

        // Sjekker journalpost
        journalpost.journalposttype shouldBe Journalposttype.INNGAAENDE

        journalpost.avsenderMottaker?.id shouldBe "01020312345"
        journalpost.avsenderMottaker?.idType shouldBe AvsenderIdType.FNR
        journalpost.avsenderMottaker?.navn shouldBe "TEST TESTESSEN"

        journalpost.bruker?.id shouldBe "01020312345"
        journalpost.bruker?.idType shouldBe BrukerIdType.FNR

        journalpost.tema shouldBe Tema.DAG
        journalpost.kanal shouldBe "NAV_NO"
        journalpost.journalfoerendeEnhet shouldBe "9999"
        journalpost.datoMottatt shouldBe LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        if (endring) {
            journalpost.tittel shouldBe "Korrigert meldekort for uke 26 - 27 (24.06.2024 - 07.07.2024) elektronisk mottatt av NAV"
        } else {
            journalpost.tittel shouldBe "Meldekort for uke 26 - 27 (24.06.2024 - 07.07.2024) elektronisk mottatt av NAV"
        }

        journalpost.tilleggsopplysninger?.size shouldBe 5
        journalpost.tilleggsopplysninger?.get(0)?.nokkel shouldBe "id"
        journalpost.tilleggsopplysninger?.get(0)?.verdi shouldBe "1"
        journalpost.tilleggsopplysninger?.get(1)?.nokkel shouldBe "kanSendesFra"
        journalpost.tilleggsopplysninger?.get(1)?.verdi shouldBe "2024-07-06"
        journalpost.tilleggsopplysninger?.get(2)?.nokkel shouldBe "userAgent"
        journalpost.tilleggsopplysninger?.get(2)?.verdi shouldBe "Some agent"
        journalpost.tilleggsopplysninger?.get(3)?.nokkel shouldBe "frontendGithubSha"
        journalpost.tilleggsopplysninger?.get(3)?.verdi shouldBe "Some image"
        journalpost.tilleggsopplysninger?.get(4)?.nokkel shouldBe "backendGithubSha"
        journalpost.tilleggsopplysninger?.get(4)?.verdi shouldBe githubSha

        journalpost.sak?.sakstype shouldBe Sakstype.GENERELL_SAK

        journalpost.dokumenter?.size shouldBe 1
        val dokument = journalpost.dokumenter?.get(0)
        if (endring) {
            dokument?.tittel shouldBe "Korrigert meldekort for uke 26 - 27 (24.06.2024 - 07.07.2024) elektronisk mottatt av NAV"
            dokument?.brevkode shouldBe "NAV 00-10.03"
        } else {
            dokument?.tittel shouldBe "Meldekort for uke 26 - 27 (24.06.2024 - 07.07.2024) elektronisk mottatt av NAV"
            dokument?.brevkode shouldBe "NAV 00-10.02"
        }

        dokument?.dokumentvarianter?.size shouldBe 2

        checkJson(journalpost, opprineligRapporteringsperiode)
        checkPdf(endring, journalpost, withHtml)
    }

    private fun checkJson(
        journalpost: Journalpost,
        opprineligRapporteringsperiode: Rapporteringsperiode,
    ) {
        journalpost.dokumenter!![0].dokumentvarianter[0].filtype shouldBe Filetype.JSON
        journalpost.dokumenter!![0].dokumentvarianter[0].variantformat shouldBe Variantformat.ORIGINAL

        val rapporteringsperiode =
            objectMapper.readValue(
                Base64.getDecoder().decode(journalpost.dokumenter!![0].dokumentvarianter[0].fysiskDokument),
                Rapporteringsperiode::class.java,
            )

        rapporteringsperiode shouldBeEqual opprineligRapporteringsperiode
    }

    private fun checkPdf(
        endring: Boolean,
        journalpost: Journalpost,
        withHtml: Boolean = false,
    ) {
        journalpost.dokumenter!![0].dokumentvarianter[1].filtype shouldBe Filetype.PDFA
        journalpost.dokumenter!![0].dokumentvarianter[1].variantformat shouldBe Variantformat.ARKIV

        var expectedFilePath = "src/test/resources/dokarkiv_expected.pdf"
        var actualFilePath = "actual.pdf"
        var diffFilePath = "diffOutput" // Uten .pdf

        if (withHtml) {
            expectedFilePath = "src/test/resources/dokarkiv_expected_with_html.pdf"
            actualFilePath = "actual_with_html.pdf"
            diffFilePath = "diffOutput_with_html" // Uten .pdf
        }

        if (endring) {
            expectedFilePath = "src/test/resources/dokarkiv_korrigert_expected.pdf"
            actualFilePath = "korrigert_actual.pdf"
            diffFilePath = "korrigert_diffOutput" // Uten .pdf
        }

        // Henter generert pdf vi har sendt
        val pdf = Base64.getDecoder().decode(journalpost.dokumenter!![0].dokumentvarianter[1].fysiskDokument)

        val actualFile = File(actualFilePath)
        actualFile.writeBytes(pdf)

        val diffFile = File("$diffFilePath.pdf")

        // Sammenligner
        val equal =
            PdfComparator<CompareResultImpl>(expectedFilePath, actualFilePath)
                .withIgnore(PageArea(1, 680, 890, 1050, 940))
                .compare()
                .writeTo(diffFilePath)

        if (!equal) {
            throw Exception("PDFene er ikke like")
        }

        actualFile.delete()
        diffFile.delete()
    }
}
