package no.nav.dagpenger.rapportering.mediator

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.hendelse.InnsendtPeriodeHendelse
import no.nav.dagpenger.rapportering.model.hendelse.SoknadInnsendtHendelse
import no.nav.dagpenger.rapportering.utils.januar
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class MediatorTest {
    private val rapid = TestRapid()
    private val mediator
        get() = Mediator(rapid)
    private val testIdent = "12312312311"
    private val soknadInnsendtHendelse: SoknadInnsendtHendelse
        get() = SoknadInnsendtHendelse(UUID.randomUUID(), testIdent, LocalDateTime.now(), UUID.randomUUID())

    @Test
    fun mediatorflyt() {
        mediator.behandle(soknadInnsendtHendelse)
    }

    @Test
    fun `kan behandle innsendt periode`() {
        val innsendtPeriodeHendelse =
            InnsendtPeriodeHendelse(UUID.randomUUID(), testIdent, 1, Periode(1.januar, 14.januar))
        mediator.behandle(innsendtPeriodeHendelse)

        rapid.inspektør.size shouldBe 1
        rapid.inspektør.message(0).let {
            it["@event_name"].asText() shouldBe "rapporteringsperiode_innsendt_hendelse"
            it["ident"].asText() shouldBe testIdent
            it["rapporteringsperiodeId"].asLong() shouldBe 1
            it["fom"].asText() shouldBe 1.januar.toString()
            it["tom"].asText() shouldBe 14.januar.toString()
        }
    }
}
