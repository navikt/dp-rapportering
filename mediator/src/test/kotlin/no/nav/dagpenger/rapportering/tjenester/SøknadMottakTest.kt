package no.nav.dagpenger.rapportering.tjenester

import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.rapportering.Mediator
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class SøknadMottakTest {
    private val testIdent = "123"
    private val testRapid = TestRapid()
    private val mockMediator = mockk<Mediator>()

    @BeforeEach
    fun setup() {
        SøknadMottak(testRapid, mockMediator)
    }

    @Test
    fun `Skal behandle innsending_ferdigstilt event for type NySøknad`() {
        val slot = slot<SøknadInnsendtHendelse>()
        every { mockMediator.behandle(capture(slot)) } just Runs

        testRapid.sendTestMessage(innsendingFerdigstiltHendelse(ident = testIdent))

        verify(exactly = 1) { mockMediator.behandle(any<SøknadInnsendtHendelse>()) }
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
