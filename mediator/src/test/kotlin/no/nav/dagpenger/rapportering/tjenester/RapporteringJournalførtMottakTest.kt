package no.nav.dagpenger.rapportering.tjenester

import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.hendelser.RapporteringJournalførtHendelse
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.util.UUID

class RapporteringJournalførtMottakTest {
    private val rapid = TestRapid()
    private val mediator = mockk<IHendelseMediator>(relaxed = true)

    init {
        RapporteringJournalførtMottak(rapid, mediator)
    }

    @Test
    fun `vi tar imot og håndterer rapportering journalført hendelser`() {
        rapid.sendTestMessage(løstBehovJSON)

        verify {
            mediator.behandle(any<RapporteringJournalførtHendelse>())
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
      "meldingsreferanseId":"d0ce2eef-ab53-4b06-acf3-4c85386dc561",
      "ident": "ident123",
      "JournalføreRapportering":{
          "periodeId": "periodeId123",
          "json": "{\"key1\": \"value1\"}",
          "urn": "urn:vedlegg:periodeId/netto.pdf"
      },
      "@løsning": {
        "journalpostId": "123456"
      },
      "@id": "30ef9625-196a-445b-9b4e-67e0e6a5118d",
      "@opprettet": "2023-10-23T18:53:08.056035121",
      "system_read_count": 0,
      "system_participating_services":[{"id": "30ef9625-196a-445b-9b4e-67e0e6a5118d", "service": "dp-rapportering"}]
    }
    """.trimIndent()
