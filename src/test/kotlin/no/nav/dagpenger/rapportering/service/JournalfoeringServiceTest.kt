package no.nav.dagpenger.rapportering.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import de.redsix.pdfcompare.CompareResultImpl
import de.redsix.pdfcompare.PageArea
import de.redsix.pdfcompare.PdfComparator
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.util.toByteArray
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writer
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Journalpost
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepository
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

class JournalfoeringServiceTest {
    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun `kan opprette og sende journalpost`() {
        val dokarkivUrl = "https://dokarkiv.nav.no"

        System.setProperty("DOKARKIV_HOST", dokarkivUrl)
        System.setProperty("DOKARKIV_AUDIENCE", "test.test.dokarkiv")
        System.setProperty("AZURE_APP_WELL_KNOWN_URL", "test.test.dokarkiv")

        // Mock TokenProvider
        fun mockTokenProvider() =
            { _: String ->
                "token"
            }

        // Mock JournalfoeringRepository
        val journalfoeringRepository = mockk<JournalfoeringRepository>()
        every { journalfoeringRepository.lagreJournalpostData(eq(2), eq(3), eq(1)) } just runs
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

        val journalfoeringService =
            JournalfoeringService(journalfoeringRepository, dokarkivUrl, mockTokenProvider(), mockEngine)

        // Testdata
        val fom = LocalDate.of(2024, 6, 24)

        val rapporteringsperiode =
            Rapporteringsperiode(
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
                TilUtfylling,
                true,
            )

        // Kjører
        runBlocking {
            journalfoeringService.journalfoer("01020312345", 0, rapporteringsperiode)
        }

        // Sjekker
        val expectedFilePath = "src/test/resources/dokarkiv_expected.pdf"
        val actualFilePath = "actual.pdf"
        val diffFilePath = "pdf_generator_diffOutput" // Uten .pdf

        mockEngine.requestHistory.size shouldBe 1
        mockEngine.responseHistory.size shouldBe 1

        // Henter generert pdf vi har sendt
        val body = mockEngine.requestHistory[0].body as OutgoingContent.WriteChannelContent
        val channel = GlobalScope.writer(Dispatchers.IO) { body.writeTo(channel) }.channel
        var bodyString: String

        runBlocking {
            bodyString = String(channel.toByteArray())
        }

        val objectMapper =
            ObjectMapper()
                .registerKotlinModule()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        val journalpost = objectMapper.readValue(bodyString, Journalpost::class.java)
        val pdf = Base64.getDecoder().decode(journalpost!!.dokumenter!![0].dokumentvarianter[1].fysiskDokument)

        val actualFile = File(actualFilePath)
        actualFile.writeBytes(pdf)

        val diffFile = File("$diffFilePath.pdf")

        // Sammenligner
        val equal =
            PdfComparator<CompareResultImpl>(expectedFilePath, actualFilePath)
                .withIgnore(PageArea(1, 720, 915, 1090, 960))
                .compare()
                .writeTo(diffFilePath)

        if (!equal) {
            throw Exception("PDFene er ikke like")
        }

        actualFile.delete()
        diffFile.delete()
    }
}
