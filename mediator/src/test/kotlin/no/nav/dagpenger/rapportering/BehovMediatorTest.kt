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
    fun `slår sammen flere behov på samme hendelse og publiserer en felles pakke`() {
        val hendelse = TestHendelse()
        hendelse.behov(
            MineBehov.Virkningsdatoer,
            "Trenger datoer",
            mapOf(
                "felt_fra_A" to "A",
                "overlappMedLikVerdi" to "C",
                "overlappMedUlikVerdi" to "C",
            ),
        )
        hendelse.behov(
            MineBehov.Innsendingstidspunkt,
            "Trenger innsendingstidspunkt",
            mapOf(
                "felt_fra_B" to "B",
                "overlappMedLikVerdi" to "C",
                "overlappMedUlikVerdi" to "D",
            ),
        )

        mediator.håndter(hendelse)

        with(rapid.inspektør) {
            size shouldBe 1
            message(0)["@behov"].map { it.asText() } shouldBe listOf("Virkningsdatoer", "Innsendingstidspunkt")
            field(0, "Virkningsdatoer")["felt_fra_A"].asText() shouldBe "A"
            field(0, "Innsendingstidspunkt")["felt_fra_B"].asText() shouldBe "B"
        }
    }

    @Test
    fun `feiler om samme hendelse fører til to behov av samme type med ulike detaljer`() {
        val hendelse = TestHendelse()
        hendelse.behov(
            MineBehov.Innsendingstidspunkt,
            "Trenger innsendingstidspunkt",
            mapOf("overlappMedUlikVerdi" to "D"),
        )
        hendelse.behov(
            MineBehov.Innsendingstidspunkt,
            "Trenger innsendingstidspunkt",
            mapOf("overlappMedUlikVerdi" to "E"),
        )

        assertThrows<IllegalArgumentException> {
            mediator.håndter(hendelse)
        }
    }

    private class TestHendelse : PersonHendelse(UUID.randomUUID(), "123", Aktivitetslogg())
}
