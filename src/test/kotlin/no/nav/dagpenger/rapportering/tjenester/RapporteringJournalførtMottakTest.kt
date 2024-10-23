package no.nav.dagpenger.rapportering.tjenester

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepository
import no.nav.dagpenger.rapportering.repository.KallLoggRepository
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RapporteringJournalførtMottakTest {
    private val testRapid = TestRapid()
    private val mockJournalfoeringRepository = mockk<JournalfoeringRepository>()
    private val mockKallLoggRepository = mockk<KallLoggRepository>()

    @BeforeEach
    fun setup() {
        RapporteringJournalførtMottak(testRapid, mockJournalfoeringRepository, mockKallLoggRepository)
    }

    @Test
    fun `Vi tar imot og håndterer rapportering journalført hendelser`() {
        coEvery { mockJournalfoeringRepository.lagreJournalpostData(any(), any(), any()) } just runs

        val response = slot<String>()
        coEvery { mockKallLoggRepository.lagreResponse(any(), any(), capture(response)) } just runs

        testRapid.sendTestMessage(løstBehovJSON)

        coVerify(exactly = 1) {
            mockJournalfoeringRepository.lagreJournalpostData(eq(123456), eq(0), eq(1234567890))
            mockKallLoggRepository.lagreResponse(eq(12345), eq(200), any())
        }

        val jsonNode = ObjectMapper().readTree(response.captured)

        jsonNode["JournalføreRapportering"].asText() shouldBe "SE TILSVARENDE REQUEST"
    }
}

@Language("JSON")
private val løstBehovJSON =
    """
    {
      "@event_name": "behov",
      "@behovId": "34f6743c-bd9a-4902-ae68-fae0171b1e68",
      "@behov": [
        "JournalføreRapportering"
      ],
      "meldingsreferanseId": "d0ce2eef-ab53-4b06-acf3-4c85386dc561",
      "ident": "01020312345",
      "JournalføreRapportering": {
        "periodeId": "1234567890",
        "brevkode": "NAV 00-10.02",
        "tittel": "Meldekort for uke 42-43 (14.10.2024 - 27.10.2024) elektronisk mottatt av NAV",
        "json": "{\"key1\": \"value1\"}",
        "pdf": "UERG",
        "tilleggsopplysninger": [],
        "kallLoggId": "12345"
      },
      "@final": true,
      "@løsning": {
        "JournalføreRapportering": "123456"
      },
      "@id": "30ef9625-196a-445b-9b4e-67e0e6a5118d",
      "@opprettet": "2023-10-23T18:53:08.056035121",
      "system_read_count": 0,
      "system_participating_services": [
        {
          "id": "30ef9625-196a-445b-9b4e-67e0e6a5118d",
          "service": "dp-rapportering"
        }
      ]
    }
    """.trimIndent()
