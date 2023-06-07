package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.hendelser.RapporteringsfristHendelse
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class UtsendingsObserverTest {

    @Test
    fun `Skal sende ut riktig melding ved rapportering_innsendt_hendelse`() {
        val testRapid = TestRapid()
        val observer =
            UtsendingsObserver(
                testRapid,
                RapporteringsfristHendelse(UUID.randomUUID(), "123", LocalDate.now().plusDays(10)),
            )

        observer.rapporteringsperiodeInnsendt(
            RapporteringsperiodeObserver.RapporteringsperiodeInnsendt(
                UUID.randomUUID(),
                fom = LocalDate.now().minusDays(1),
                tom = LocalDate.now().plusDays(13),
                dager = listOf(
                    Dag(LocalDate.now(), mutableListOf(Aktivitet.Arbeid(LocalDate.now(), 5))),
                ),
            ),
        )

        testRapid.inspektør.size shouldBe 1
    }
}
