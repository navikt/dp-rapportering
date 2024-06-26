package no.nav.dagpenger.rapportering.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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
    @Test
    fun `kan opprette og sende journalpost`() {
        val dokarkivUrl = "https://dokarkiv.nav.no"

        System.setProperty("DOKARKIV_HOST", dokarkivUrl)
        System.setProperty("DOKARKIV_AUDIENCE", "test.test.dokarkiv")
        System.setProperty("AZURE_APP_WELL_KNOWN_URL", "test.test.dokarkiv")

        fun mockTokenProvider() =
            { _: String ->
                "token"
            }

        val journalfoeringRepository = mockk<JournalfoeringRepository>()
        every { journalfoeringRepository.lagreJournalpostData(eq(2), eq(3), eq(1)) } just runs
        every { journalfoeringRepository.hentMidlertidigLagredeJournalposter() } returns emptyList()

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

        val rapporteringsperiode =
            Rapporteringsperiode(
                1L,
                Periode(LocalDate.now().minusDays(13), LocalDate.now()),
                listOf(
                    Dag(
                        LocalDate.now().minusDays(13),
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
                        LocalDate.now().minusDays(12),
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
                LocalDate.now(),
                true,
                true,
                0.0,
                TilUtfylling,
                true,
            )

        runBlocking {
            journalfoeringService.journalfoer("01020312345", 0, rapporteringsperiode)
        }

        mockEngine.requestHistory.size shouldBe 1
        mockEngine.responseHistory.size shouldBe 1

        // Lagrer pdf vi har sendt
        val body = mockEngine.requestHistory[0].body as OutgoingContent.WriteChannelContent
        val channel = GlobalScope.writer(Dispatchers.IO) { body.writeTo(channel) }.channel
        var bodyString = ""

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

        val actualFile = File("test.pdf")
        actualFile.writeBytes(pdf)

        // TODO: Sjekk PDF

        actualFile.delete()
    }
}
