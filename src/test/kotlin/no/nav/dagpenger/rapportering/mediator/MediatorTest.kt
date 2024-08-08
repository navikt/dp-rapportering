package no.nav.dagpenger.rapportering.mediator

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Arbeid
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Utdanning
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
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
        val rapporteringsperiode =
            Rapporteringsperiode(
                id = 1392,
                periode = Periode(fraOgMed = 1.januar, tilOgMed = 14.januar),
                dager =
                    (0..13).map { dag ->
                        Dag(
                            dato = 1.januar.plusDays(dag.toLong()),
                            dagIndex = dag,
                            aktiviteter =
                                if (dag % 2 == 0) {
                                    emptyList()
                                } else {
                                    listOf(
                                        Aktivitet(
                                            id = UUID.randomUUID(),
                                            type = Arbeid,
                                            timer = "PT8H",
                                        ),
                                        Aktivitet(
                                            id = UUID.randomUUID(),
                                            type = Utdanning,
                                            timer = null,
                                        ),
                                    )
                                },
                        )
                    },
                kanSendesFra = 13.januar,
                kanSendes = true,
                kanKorrigeres = false,
                bruttoBelop = null,
                status = TilUtfylling,
                registrertArbeidssoker = true,
                begrunnelseKorrigering = null,
            )
        val innsendtPeriodeHendelse =
            InnsendtPeriodeHendelse(UUID.randomUUID(), testIdent, rapporteringsperiode)
        mediator.behandle(innsendtPeriodeHendelse)

        rapid.inspektør.size shouldBe 2
        rapid.inspektør.message(0).let {
            it["@event_name"].asText() shouldBe "rapporteringsperiode_innsendt_hendelse"
            it["ident"].asText() shouldBe testIdent
            it["rapporteringsperiodeId"].asLong() shouldBe 1392
            it["fom"].asText() shouldBe 1.januar.toString()
            it["tom"].asText() shouldBe 14.januar.toString()
            it["dager"].let { dager ->
                dager.size() shouldBe 14
                dager.first()["dato"].asText() shouldBe 1.januar.toString()
                dager.first()["aktiviteter"].size() shouldBe 0
                dager.last()["dato"].asText() shouldBe 14.januar.toString()
                dager.last()["aktiviteter"].size() shouldBe 2
                dager.last()["aktiviteter"].first()["type"].asText() shouldBe "Arbeid"
                dager.last()["aktiviteter"].first()["timer"].asText() shouldBe "PT8H"
                dager.last()["aktiviteter"].last()["type"].asText() shouldBe "Utdanning"
                dager.last()["aktiviteter"].last()["timer"].isNull shouldBe true
            }
        }
        rapid.inspektør.message(1).let {
            it["@event_name"].asText() shouldBe "arbeidssoker_neste_periode_hendelse"
            it["ident"].asText() shouldBe testIdent
            it["fom"].asText() shouldBe rapporteringsperiode.kanSendesFra.plusDays(1).toString()
            it["tom"].asText() shouldBe rapporteringsperiode.kanSendesFra.plusDays(14).toString()
            it["registrertArbeidssoker"].asBoolean() shouldBe true
        }
    }
}
