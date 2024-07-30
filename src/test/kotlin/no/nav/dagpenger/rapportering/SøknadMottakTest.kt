package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.rapportering.mediator.Mediator
import no.nav.dagpenger.rapportering.model.hendelse.SoknadInnsendtHendelse
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class SøknadMottakTest {
    private val testIdent = "12312312311"
    private val testRapid = TestRapid()
    private val mockMediator = mockk<Mediator>()

    @BeforeEach
    fun setup() {
        SøknadMottak(testRapid, mockMediator)
    }

    @Test
    fun `Skal behandle innsending_ferdigstilt event for type NySøknad`() {
        val slot = slot<SoknadInnsendtHendelse>()
        justRun { mockMediator.behandle(capture(slot)) }

        testRapid.sendTestMessage(innsendingFerdigstiltHendelse(ident = testIdent))

        verify(exactly = 1) { mockMediator.behandle(any<SoknadInnsendtHendelse>()) }
        slot.captured.ident() shouldBe testIdent
    }
}

fun innsendingFerdigstiltHendelse(
    ident: String,
    søknadId: UUID = UUID.randomUUID(),
): String =
    //language=JSON
    """
    {
      "type": "NySøknad",
      "fødselsnummer": "$ident",
      "søknadsData": {
        "søknad_uuid": "$søknadId"
      },
      "@event_name": "innsending_ferdigstilt"
    } 
    """.trimIndent()
