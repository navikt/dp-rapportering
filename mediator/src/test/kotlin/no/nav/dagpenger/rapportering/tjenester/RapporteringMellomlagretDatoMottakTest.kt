package no.nav.dagpenger.rapportering.tjenester

import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.hendelser.RapporteringMellomlagretHendelse
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class RapporteringMellomlagretDatoMottakTest {
    private val rapid = TestRapid()
    private val mediator = mockk<IHendelseMediator>(relaxed = true)
    private val mottak = RapporteringspliktDatoMottak(rapid, mediator)

    @Test
    fun `vi tar imot og håndterer rapportering mellomlagret hendelser`() {
        rapid.sendTestMessage(løstBehovJSON)

        verify {
            mediator.behandle(any<RapporteringMellomlagretHendelse>())
        }
    }
}

@Language("JSON")
private val løstBehovJSON = """
    {
      "@id": "${UUID.randomUUID()}",
      "@event_name": "behov",
      "@behov": [
        "MellomlagreRapportering"
      ],
      "@opprettet": "${LocalDateTime.now()}",
      "ident": "ident123",
      "periodeId": "periodeId123",
      "@løsning": {
        "MellomlagreRapportering": "${LocalDate.now()}",
        "json": "{}"
      },
      "@final": true
    }
""".trimIndent()
