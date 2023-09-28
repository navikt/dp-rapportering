package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.helpers.januar
import no.nav.dagpenger.rapportering.hendelser.BeregningsdatoPassertHendelse
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class UtsendingsObserverTest {
    @Test
    fun foo() {
        assertEquals(
            7 * 24 * 60 * 60 * 1000,
            7.days.inWholeMilliseconds,
        )
    }

    @Test
    fun `Skal sende ut riktig melding ved rapportering_innsendt_hendelse`() {
        val testRapid = TestRapid()
        val observer =
            UtsendingsObserver(
                testRapid,
                BeregningsdatoPassertHendelse(UUID.randomUUID(), "123", LocalDate.now().plusDays(10)),
            )
        val fom = 1.januar
        val tom = 14.januar
        val sakId = UUID.randomUUID()
        val rapporteringsperiodeId = UUID.randomUUID()
        val korrigererId = UUID.randomUUID()
        observer.rapporteringsperiodeInnsendt(
            RapporteringsperiodeObserver.RapporteringsperiodeInnsendt(
                rapporteringsperiodeId = rapporteringsperiodeId,
                fom = fom,
                tom = tom,
                dager = fom.datesUntil(tom.plusDays(1)).map {
                    Dag(it, mutableListOf(Aktivitet.Arbeid(it, 5)))
                }.toList(),
                sakId = sakId,
                korrigerer = korrigererId,
            ),
        )

        testRapid.inspektør.size shouldBe 1
        testRapid.inspektør.message(0).let {
            it["@event_name"].asText() shouldBe "rapporteringsperiode_innsendt_hendelse"
            it["ident"].asText() shouldBe "123"
            it["rapporteringsId"].asText() shouldBe rapporteringsperiodeId.toString()
            it["fom"].asLocalDate() shouldBe 1.januar
            it["tom"].asLocalDate() shouldBe 14.januar

            it["dager"].let { dager ->
                dager.size() shouldBe 14
                dager.first()["dato"].asLocalDate() shouldBe 1.januar
            }

            it["dager"].first()["aktiviteter"].let { aktiviteter ->
                aktiviteter.size() shouldBe 1
                aktiviteter.single()["type"].asText() shouldBe "Arbeid"
                Duration.parse(aktiviteter.single()["tid"].asText()) shouldBe 5.hours
            }

            it["sakId"].asText() shouldBe sakId.toString()
            it["korrigerer"].asText() shouldBe korrigererId.toString()
        }
    }
}
