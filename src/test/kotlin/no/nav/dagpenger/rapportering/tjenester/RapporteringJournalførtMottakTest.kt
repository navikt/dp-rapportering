package no.nav.dagpenger.rapportering.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepository
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class RapporteringJournalførtMottakTest {
    private val testRapid = TestRapid()
    private val mockJournalfoeringRepository = mockk<JournalfoeringRepository>()

    @BeforeEach
    fun setup() {
        RapporteringJournalførtMottak(testRapid, mockJournalfoeringRepository)
    }

    @Test
    fun `vi tar imot og håndterer rapportering journalført hendelser`() {
        coEvery { mockJournalfoeringRepository.lagreJournalpostData(any(), any(), any()) } just runs

        testRapid.sendTestMessage(løstBehovJSON)

        coVerify(exactly = 1) {
            mockJournalfoeringRepository.lagreJournalpostData(eq(123456), eq(0), eq(1234567890))
        }
    }
}

@Language("JSON")
private val løstBehovJSON =
    """
    {
      "@event_name": "behov",
      "@behovId": "${UUID.randomUUID()}",
      "@behov": [
        "JournalføreRapportering"
      ],
      "meldingsreferanseId": "d0ce2eef-ab53-4b06-acf3-4c85386dc561",
      "ident": "01020312345",
      "JournalføreRapportering": {
        "periodeId": "1234567890",
        "brevkode": "NAV 00-10.02",
        "json": "{\"key1\": \"value1\"}",
        "pdf": "UERG",
        "tilleggsopplysninger": ""
      },
      "@final": true,
      "@løsning": {
        "journalpostId": "123456"
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
