package no.nav.dagpenger.rapportering.tjenester

import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.rapportering.Mediator
import no.nav.dagpenger.rapportering.meldinger.SøknadInnsendtMelding
import no.nav.dagpenger.rapportering.tjenester.Meldingsfabrikk.`innsending ferdigstilt hendelse`
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Test
import java.util.UUID

class SøknadMottakTest {
    private val testIdent = "123"

    @Test
    fun `Skal behandle innsending_ferdigstilt event for type NySøknad`() {
        val slot = slot<SøknadInnsendtMelding>()
        val mockMediator = mockk<Mediator>().also {
            every { it.behandle(capture(slot), any(), any()) } just Runs
        }

        TestRapid().let { testRapid ->
            SøknadMottak(
                testRapid,
                mockMediator,
            )
            val ident = testIdent

            testRapid.sendTestMessage(
                `innsending ferdigstilt hendelse`(
                    ident = ident,
                ),
            )

            verify(exactly = 1) { mockMediator.behandle(any(), any(), any()) }

            slot.captured.let { hendelse ->
                hendelse.ident shouldBe ident
            }
        }
    }
}

private object Meldingsfabrikk {
    //language=json
    fun `innsending ferdigstilt hendelse`(
        ident: String,
        søknadId: UUID = UUID.randomUUID(),
    ): String = //language=JSON
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
}
