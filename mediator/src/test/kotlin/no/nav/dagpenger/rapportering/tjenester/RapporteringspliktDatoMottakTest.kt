package no.nav.dagpenger.rapportering.tjenester

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.hendelser.RapporteringspliktDatoHendelse
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class RapporteringspliktDatoMottakTest {
    private val testRapid = TestRapid()
    private val mockMediator = mockk<IHendelseMediator>()

    @BeforeEach
    fun setup() {
        RapporteringspliktDatoMottak(testRapid, mockMediator)
    }

    @Test
    fun `vi tar imot og håndterer rapporteringspliktdato hendelser`() {
        every { mockMediator.behandle(any<RapporteringspliktDatoHendelse>()) } just runs

        testRapid.sendTestMessage(løstBehovJSON)

        verify(exactly = 1) { mockMediator.behandle(any<RapporteringspliktDatoHendelse>()) }
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
