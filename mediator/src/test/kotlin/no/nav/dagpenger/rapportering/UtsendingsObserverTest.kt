package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.helpers.januar
import no.nav.dagpenger.rapportering.hendelser.RapporteringsfristHendelse
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

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
                fom = 1.januar,
                tom = 14.januar,
                dager = listOf(
                    Dag(5.januar, mutableListOf(Aktivitet.Arbeid(5.januar, 5))),
                ),
            ),
        )

        testRapid.inspektør.size shouldBe 1
        testRapid.inspektør.message(0).let {
            it["@event_name"].asText() shouldBe "rapporteringsperiode_innsendt_hendelse"
            it["ident"].asText() shouldBe "123"
            it["fom"].asLocalDate() shouldBe 1.januar
            it["tom"].asLocalDate() shouldBe 14.januar

            it["dager"].let { dager ->
                dager.size() shouldBe 1
                dager.single()["dato"].asLocalDate() shouldBe 5.januar
            }

            it["dager"].single()["aktiviteter"].let { aktiviteter ->
                aktiviteter.size() shouldBe 1
                aktiviteter.single()["type"].asText() shouldBe "Arbeid"
                Duration.parse(aktiviteter.single()["tid"].asText()) shouldBe 5.hours
            }
        }
    }
}
