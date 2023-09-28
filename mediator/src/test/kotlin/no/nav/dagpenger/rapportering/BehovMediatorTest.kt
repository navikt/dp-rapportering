package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.PersonHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringMidlertidigJournalførtHendelse
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

    @Test
    fun `kan sende ut behov om journalføre rapportering`() {
        val ident = "01020312345"
        val periodeId = UUID.randomUUID()
        val hendelse = GodkjennPeriodeHendelse(ident, periodeId)
        hendelse.behov(
            MineBehov.JournalføreRapportering,
            "Trenger å journalføre rapportering",
            mapOf(
                "periodeId" to periodeId,
                "json" to "{}",
            ),
        )

        mediator.håndter(hendelse)

        with(rapid.inspektør) {
            size shouldBe 1
            message(0)["@behov"].map { it.asText() } shouldBe listOf("JournalføreRapportering")
            field(0, "ident").asText() shouldBe ident
            field(0, "JournalføreRapportering")["json"].asText() shouldBe "{}"
            field(0, "JournalføreRapportering")["periodeId"].asText() shouldBe periodeId.toString()
        }
    }

    @Test
    fun `kan sende ut behov om rapportering journalpost`() {
        val ident = "01020312345"
        val periodeId = UUID.randomUUID()
        val journalpostId = UUID.randomUUID().toString()
        val hendelse = RapporteringMidlertidigJournalførtHendelse(ident, periodeId, journalpostId)
        hendelse.behov(
            MineBehov.OpprettPdfForRapportering,
            "Trenger å opprette PDF for å journalføre rapportering",
            mapOf(
                "periodeId" to periodeId,
                "journalpostId" to journalpostId,
                "json" to "{}",
            ),
        )

        mediator.håndter(hendelse)

        with(rapid.inspektør) {
            size shouldBe 1
            message(0)["@behov"].map { it.asText() } shouldBe listOf("OpprettPdfForRapportering")
            field(0, "ident").asText() shouldBe ident
            field(0, "OpprettPdfForRapportering")["json"].asText() shouldBe "{}"
            field(0, "OpprettPdfForRapportering")["periodeId"].asText() shouldBe periodeId.toString()
            field(0, "OpprettPdfForRapportering")["journalpostId"].asText() shouldBe journalpostId
        }
    }

    private class TestHendelse : PersonHendelse(UUID.randomUUID(), "123", Aktivitetslogg())
}
