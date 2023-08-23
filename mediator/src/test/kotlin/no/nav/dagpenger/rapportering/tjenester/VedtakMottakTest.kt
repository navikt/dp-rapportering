package no.nav.dagpenger.rapportering.tjenester

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import no.nav.dagpenger.rapportering.Mediator
import no.nav.dagpenger.rapportering.hendelser.VedtakAvslåttHendelse
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
    fun `Skal behandle dagpenger_innvilget event`() {
        every { mediator.behandle(any<VedtakInnvilgetHendelse>()) } just runs
        testRapid.sendTestMessage(dagpengerInnvilgetMelding)

        verify(exactly = 1) { mediator.behandle(any<VedtakInnvilgetHendelse>()) }
    }

    @Test
    fun `Skal behandle dagpenger_avslått event`() {
        every { mediator.behandle(any<VedtakAvslåttHendelse>()) } just runs
        testRapid.sendTestMessage(dagpengerAvslåttMelding)

        verify(exactly = 1) { mediator.behandle(any<VedtakAvslåttHendelse>()) }
    }
}

@Language("JSON")
private val dagpengerInnvilgetMelding = """
    {
      "@event_name": "dagpenger_innvilget",
      "ident": "123",
      "behandlingId": "${UUID.randomUUID()}",
      "virkningsdato": "${LocalDate.now()}",
      "@id": "${UUID.randomUUID()}",
      "@opprettet": "${LocalDateTime.now()}",
      "sakId": "${UUID.randomUUID()}"
    }
""".trimIndent()

@Language("JSON")
private val dagpengerAvslåttMelding = """
    {
      "@event_name": "dagpenger_avslått",
      "ident": "123",
      "behandlingId": "${UUID.randomUUID()}",
      "virkningsdato": "${LocalDate.now()}",
      "@id": "${UUID.randomUUID()}",
      "@opprettet": "${LocalDateTime.now()}",
      "sakId": "${UUID.randomUUID()}"
    }
""".trimIndent()
