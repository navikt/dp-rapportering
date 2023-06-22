package no.nav.dagpenger.rapportering.hendelser

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.helpers.TestData.testIdent
import no.nav.dagpenger.rapportering.helpers.januar
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RapporteringspliktDatoHendelseTest {
    @Test
    fun `Velger siste dato som gjelderFra`() {
        val hendelse = RapporteringspliktDatoHendelse(
            UUID.randomUUID(),
            testIdent,
            LocalDateTime.now(),
            1.januar,
            2.januar,
        )

        hendelse.gjelderFra shouldBe 2.januar
    }
}
