package no.nav.dagpenger.rapportering.tjenester

import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.hendelser.RapporteringJournalførtHendelse
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
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
      "@id": "${UUID.randomUUID()}",
      "@event_name": "behov",
      "@behov": [
        "JournalføreRapportering"
      ],
      "@opprettet": "${LocalDateTime.now()}",
      "ident": "ident123",
      "periodeId": "periodeId123",
      "JournalføreRapportering": {},
      "@løsning": {
        "JournalføreRapportering": "${LocalDate.now()}",
        "journalpostId": "123456"
      },
      "@final": true
    }
    """.trimIndent()
