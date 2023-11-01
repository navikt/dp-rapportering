package no.nav.dagpenger.rapportering.tjenester

import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.hendelser.RapporteringspliktDatoHendelse
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class RapporteringspliktDatoMottakTest {
    private val rapid = TestRapid()
    private val mediator = mockk<IHendelseMediator>(relaxed = true)

    init {
        RapporteringspliktDatoMottak(rapid, mediator)
    }

    @Test
    fun `vi tar imot og håndterer rapporteringspliktdato hendelser`() {
        rapid.sendTestMessage(løstBehovJSON)

        verify {
            mediator.behandle(any<RapporteringspliktDatoHendelse>())
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
        "Søknadstidspunkt"
      ],
      "@opprettet": "${LocalDateTime.now()}",
      "ident": "123",
      "Søknadstidspunkt": {
        "søknad_uuid": "${UUID.randomUUID()}"
      },
      "@løsning": {
        "Søknadstidspunkt": "${LocalDate.now()}"
      },
      "@final": true
    }
    """.trimIndent()
