package no.nav.dagpenger.rapportering.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
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
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.mockk.every
import io.mockk.just
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.connector.createHttpClient
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.MidlertidigLagretData
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepository
import no.nav.dagpenger.rapportering.repository.Postgres.database
import no.nav.dagpenger.rapportering.utils.MetricsTestUtil.meterRegistry
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

class JournalfoeringServiceTest {
    private val ident = "01020312345"
    private val navn = "Test Testesen"
    private val userAgent = "Some agent"
    private val frontendGithubSha = "frontendabcdwfg"
    private val backendGithubSha = "backendabcdwfg"
    private val headers =
        Headers.build {
            append("useragent", userAgent)
            append("githubsha", frontendGithubSha)
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
    fun `Kan lagre data midlertidig ved feil og sende paa nytt`() {
        setProperties()

        // Mock svar fra PDFgenerator
        val pdf = this::class.java.getResource("/expected.pdf")!!.readBytes()
        val mockPdfGeneratorEngine =
            MockEngine { _ ->
                respond(
                    content = ByteReadChannel(pdf),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/pdf"),
                )
            }

        // Mock
        val rapidsConnection = mockk<RapidsConnection>()

        val journalfoeringRepository = mockk<JournalfoeringRepository>()
        every { journalfoeringRepository.lagreDataMidlertidig(any()) } just runs
        every { journalfoeringRepository.lagreJournalpostData(any(), any(), any()) } just runs
        every { journalfoeringRepository.oppdaterMidlertidigLagretData(any(), any()) } just runs
        every { journalfoeringRepository.sletteMidlertidigLagretData(any()) } just runs
        every { journalfoeringRepository.hentMidlertidigLagretData() } returns
            listOf(
                Triple(
                    "1",
                    MidlertidigLagretData(
                        ident,
                        navn,
                        headers,
                        createRapporteringsperiode(false),
                    ),
                    0,
                ),
            )

        val journalfoeringService =
            JournalfoeringService(
                rapidsConnection,
                journalfoeringRepository,
                meterRegistry,
                createHttpClient(mockPdfGeneratorEngine),
            )

        // Oppretter rapporteringsperiode
        val rapporteringsperiode = createRapporteringsperiode(false)

        // Prøver å journalføre
        runBlocking {
            journalfoeringService.journalfoer(ident, navn, headers, rapporteringsperiode)
        }

        // Får feil og sjekker at JournalfoeringService lagrer data midlertidig
        verify { journalfoeringRepository.lagreDataMidlertidig(any()) }

        runBlocking {
            journalfoeringService.journalfoerPaaNytt()
        }

        // Sjekker at JournalfoeringService prøvde å sende journalpost på nytt, fikk feil og oppdaterte retries
        verify { journalfoeringRepository.hentMidlertidigLagretData() }
        verify { journalfoeringRepository.oppdaterMidlertidigLagretData("1", 1) }

        every { rapidsConnection.publish(any(), any()) } just runs

        runBlocking {
            journalfoeringService.journalfoerPaaNytt()
        }

        verify { journalfoeringRepository.sletteMidlertidigLagretData("1") }
    }

    private fun setProperties() {
        System.setProperty(
            "DB_JDBC_URL",
            "${database.jdbcUrl}&user=${database.username}&password=${database.password}",
        )
        System.setProperty("PDF_GENERATOR_URL", "pdf-generator")
        System.setProperty("GITHUB_SHA", backendGithubSha)
    }

    private fun test(endring: Boolean = false) {
        setProperties()

        // Mock
        val message = slot<String>()
        val rapidsConnection = mockk<RapidsConnection>()
        justRun { rapidsConnection.publish(eq(ident), capture(message)) }

        val journalfoeringRepository = mockk<JournalfoeringRepository>()
        justRun { journalfoeringRepository.lagreJournalpostData(eq(2), eq(3), eq(1)) }
        every { journalfoeringRepository.hentMidlertidigLagretData() } returns emptyList()

        // Mock svar fra PDFgenerator
        val pdf =
            if (endring) {
                this::class.java.getResource("/korrigert_expected.pdf")!!.readBytes()
            } else {
                this::class.java.getResource("/expected.pdf")!!.readBytes()
            }

        val mockPdfGeneratorEngine =
            MockEngine { _ ->
                respond(
                    content = ByteReadChannel(pdf),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/pdf"),
                )
            }

        val journalfoeringService =
            JournalfoeringService(
                rapidsConnection,
                journalfoeringRepository,
                meterRegistry,
                createHttpClient(mockPdfGeneratorEngine),
            )

        val rapporteringsperiode = createRapporteringsperiode(endring)

        // Kjører
        runBlocking {
            journalfoeringService.journalfoer(ident, navn, headers, rapporteringsperiode)
        }

        runBlocking {
            checkMessage(endring, message.captured, rapporteringsperiode)
        }
    }

    private fun createRapporteringsperiode(
        endring: Boolean,
        html: String? = null,
    ): Rapporteringsperiode {
        val fom = LocalDate.of(2024, 6, 24)

        return Rapporteringsperiode(
            1L,
            Periode(fom, fom.plusDays(13)),
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
            fom.plusDays(12),
            true,
            true,
            0.0,
            null,
            if (endring) RapporteringsperiodeStatus.Endret else TilUtfylling,
            LocalDate.now(),
            true,
            null,
            null,
            html,
        )
    }

    private fun checkMessage(
        endring: Boolean,
        message: String,
        rapporteringsperiode: Rapporteringsperiode,
    ) {
        val jsonNode = objectMapper.readTree(message)

        jsonNode.get("@event_name").asText() shouldBe "behov"
        jsonNode.get("@behov").asIterable().iterator().next().asText() shouldBe "JournalføreRapportering"

        val behov = jsonNode.get("JournalføreRapportering")
        behov.get("periodeId").asInt() shouldBe 1
        if (endring) {
            behov.get("brevkode").asText() shouldBe "NAV 00-10.03"
        } else {
            behov.get("brevkode").asText() shouldBe "NAV 00-10.02"
        }

        checkJson(behov.get("json").asText(), rapporteringsperiode)
        checkPdf(endring, behov.get("pdf").asText())

        val to = behov.get("tilleggsopplysninger")

        to.get(0).get("first").asText() shouldBe "periodeId"
        to.get(0).get("second").asLong() shouldBe rapporteringsperiode.id
        to.get(1).get("first").asText() shouldBe "kanSendesFra"
        to.get(1).get("second").asText() shouldBe rapporteringsperiode.kanSendesFra.format(DateTimeFormatter.ISO_DATE)
        to.get(2).get("first").asText() shouldBe "userAgent"
        to.get(2).get("second").asText() shouldBe userAgent
        to.get(3).get("first").asText() shouldBe "frontendGithubSha"
        to.get(3).get("second").asText() shouldBe frontendGithubSha
        to.get(4).get("first").asText() shouldBe "backendGithubSha"
        to.get(4).get("second").asText() shouldBe backendGithubSha
    }

    private fun checkJson(
        json: String,
        opprineligRapporteringsperiode: Rapporteringsperiode,
    ) {
        val rapporteringsperiode =
            objectMapper.readValue(
                json,
                Rapporteringsperiode::class.java,
            )

        rapporteringsperiode shouldBeEqual opprineligRapporteringsperiode
    }

    private fun checkPdf(
        endring: Boolean,
        base64EncodedPdf: String,
    ) {
        var expectedFilePath = "src/test/resources/expected.pdf"
        var actualFilePath = "actual.pdf"
        var diffFilePath = "diffOutput" // Uten .pdf

        if (endring) {
            expectedFilePath = "src/test/resources/korrigert_expected.pdf"
            actualFilePath = "korrigert_actual.pdf"
            diffFilePath = "korrigert_diffOutput" // Uten .pdf
        }

        // Henter generert pdf vi har sendt
        val pdf = Base64.getDecoder().decode(base64EncodedPdf)

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
