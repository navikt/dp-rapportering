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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.AvsenderIdType
import no.nav.dagpenger.rapportering.model.BrukerIdType
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Filetype
import no.nav.dagpenger.rapportering.model.Journalpost
import no.nav.dagpenger.rapportering.model.Journalposttype
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.model.Sakstype
import no.nav.dagpenger.rapportering.model.Tema
import no.nav.dagpenger.rapportering.model.Variantformat
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepository
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

class JournalfoeringServiceTest {
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
    fun `Kan opprette og sende journalpost ved korrigering`() {
        test(true)
    }

    private fun test(korrigering: Boolean = false) {
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

        val rapporteringsperiode = createRapporteringsperiode(korrigering)

        // Kjører
        runBlocking {
            journalfoeringService.journalfoer("01020312345", 0, rapporteringsperiode)
        }

        // Sjekker
        mockEngine.requestHistory.size shouldBe 1
        mockEngine.responseHistory.size shouldBe 1

        runBlocking {
            checkJournalpost(korrigering, mockEngine.requestHistory[0].body, rapporteringsperiode)
        }
    }

    private fun createRapporteringsperiode(korrigering: Boolean): Rapporteringsperiode {
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
            if (korrigering) RapporteringsperiodeStatus.Korrigert else TilUtfylling,
            true,
        )
    }

    private suspend fun checkJournalpost(
        korrigering: Boolean,
        content: OutgoingContent,
        opprineligRapporteringsperiode: Rapporteringsperiode,
    ) {
        // Henter Journalpost vi har sendt
        val body = content as OutgoingContent.WriteChannelContent
        val channel = GlobalScope.writer(Dispatchers.IO) { body.writeTo(channel) }.channel
        val bodyString = String(channel.toByteArray())

        val journalpost = objectMapper.readValue(bodyString, Journalpost::class.java)

        // Sjekker journalpost
        journalpost.journalposttype shouldBe Journalposttype.INNGAAENDE

        journalpost.avsenderMottaker?.id shouldBe "01020312345"
        journalpost.avsenderMottaker?.idType shouldBe AvsenderIdType.FNR
        journalpost.avsenderMottaker?.navn shouldBe "NAVN"

        journalpost.bruker?.id shouldBe "01020312345"
        journalpost.bruker?.idType shouldBe BrukerIdType.FNR

        journalpost.tema shouldBe Tema.DAG
        journalpost.kanal shouldBe "NAV_NO"
        journalpost.journalfoerendeEnhet shouldBe "9999"
        journalpost.datoMottatt shouldBe LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        if (korrigering) {
            journalpost.tittel shouldBe "Korrigert meldekort for uke 26 - 27 (24.06.2024 - 07.07.2024) elektronisk mottatt av NAV"
        } else {
            journalpost.tittel shouldBe "Meldekort for uke 26 - 27 (24.06.2024 - 07.07.2024) elektronisk mottatt av NAV"
        }

        journalpost.tilleggsopplysninger?.size shouldBe 2
        journalpost.tilleggsopplysninger?.get(0)?.nokkel shouldBe "id"
        journalpost.tilleggsopplysninger?.get(0)?.verdi shouldBe "1"
        journalpost.tilleggsopplysninger?.get(1)?.nokkel shouldBe "kanSendesFra"
        journalpost.tilleggsopplysninger?.get(1)?.verdi shouldBe "2024-07-06"

        journalpost.sak?.sakstype shouldBe Sakstype.GENERELL_SAK

        journalpost.dokumenter?.size shouldBe 1
        val dokument = journalpost.dokumenter?.get(0)
        if (korrigering) {
            dokument?.tittel shouldBe "Korrigert meldekort for uke 26 - 27 (24.06.2024 - 07.07.2024) elektronisk mottatt av NAV"
            dokument?.brevkode shouldBe "NAV 00-10.03"
        } else {
            dokument?.tittel shouldBe "Meldekort for uke 26 - 27 (24.06.2024 - 07.07.2024) elektronisk mottatt av NAV"
            dokument?.brevkode shouldBe "NAV 00-10.02"
        }

        dokument?.dokumentvarianter?.size shouldBe 2

        checkJson(journalpost, opprineligRapporteringsperiode)
        checkPdf(korrigering, journalpost)
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
        korrigering: Boolean,
        journalpost: Journalpost,
    ) {
        journalpost.dokumenter!![0].dokumentvarianter[1].filtype shouldBe Filetype.PDFA
        journalpost.dokumenter!![0].dokumentvarianter[1].variantformat shouldBe Variantformat.ARKIV

        var expectedFilePath = "src/test/resources/dokarkiv_expected.pdf"
        if (korrigering) expectedFilePath = "src/test/resources/dokarkiv_korrigert_expected.pdf"
        val actualFilePath = "actual.pdf"
        val diffFilePath = "pdf_generator_diffOutput" // Uten .pdf

        // Henter generert pdf vi har sendt
        val pdf = Base64.getDecoder().decode(journalpost.dokumenter!![0].dokumentvarianter[1].fysiskDokument)

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
