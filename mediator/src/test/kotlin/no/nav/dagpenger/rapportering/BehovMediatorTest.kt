package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.rapportering.hendelser.PersonHendelse
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class BehovMediatorTest {
    private val rapid = TestRapid()
    private val mediator = BehovMediator(rapid)

    @Test
    fun `kan sende ut behov om søknadstidspunkt`() {
        val hendelse = TestHendelse()
        hendelse.behov(
            MineBehov.Søknadstidspunkt,
            "Trenger søknadstidspunkt",
            mapOf(
                "felt_fra_A" to "A",
            ),
        )

        mediator.håndter(hendelse)

        with(rapid.inspektør) {
            size shouldBe 1
            message(0)["@behov"].map { it.asText() } shouldBe listOf("Søknadstidspunkt")
            field(0, "Søknadstidspunkt")["felt_fra_A"].asText() shouldBe "A"
        }
    }

    @Test
    fun `feiler om samme hendelse fører til to behov av samme type med ulike detaljer`() {
        val hendelse = TestHendelse()
        hendelse.behov(
            MineBehov.Søknadstidspunkt,
            "Trenger søknadstidspunkt",
            mapOf("overlappMedUlikVerdi" to "D"),
        )
        hendelse.behov(
            MineBehov.Søknadstidspunkt,
            "Trenger søknadstidspunkt",
            mapOf("overlappMedUlikVerdi" to "E"),
        )

        assertThrows<IllegalArgumentException> {
            mediator.håndter(hendelse)
        }
    }

    private class TestHendelse : PersonHendelse(UUID.randomUUID(), "123", Aktivitetslogg())
}
