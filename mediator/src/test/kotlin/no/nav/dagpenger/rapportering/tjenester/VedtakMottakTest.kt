package no.nav.dagpenger.rapportering.tjenester

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import no.nav.dagpenger.rapportering.Mediator
import no.nav.dagpenger.rapportering.hendelser.VedtakInnvilgetHendelse
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class VedtakMottakTest {
    private val testRapid = TestRapid()
    private val mediator = mockk<Mediator>(relaxed = true)
    private val mottak = VedtakMottak(testRapid, mediator)

    @Test
    fun `Skal behandle vedtak_fattet event`() {
        every { mediator.behandle(any<VedtakInnvilgetHendelse>()) } just runs
        testRapid.sendTestMessage(vedtakFattetMelding)

        verify(exactly = 1) { mediator.behandle(any<VedtakInnvilgetHendelse>()) }
    }
}

@Language("JSON")
private val vedtakFattetMelding = """
    {
      "@event_name": "hovedrettighet_vedtak_fattet",
      "ident": "123",
      "behandlingId": "${UUID.randomUUID()}",
      "virkningsdato": "${LocalDate.now()}",
      "utfall": "Innvilget",
      "@id": "${UUID.randomUUID()}",
      "@opprettet": "${LocalDateTime.now()}"
    }
""".trimIndent()
