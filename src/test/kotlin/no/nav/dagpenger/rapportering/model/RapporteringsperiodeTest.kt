package no.nav.dagpenger.rapportering.model

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.api.rapporteringsperiodeFor
import no.nav.dagpenger.rapportering.model.PeriodeData.Kilde
import no.nav.dagpenger.rapportering.model.PeriodeData.OpprettetAv
import no.nav.dagpenger.rapportering.model.PeriodeData.Type
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class RapporteringsperiodeTest {
    @Test
    fun `Arbeidet = true hvis bruker jobbet`() {
        val rapporteringsperiode =
            rapporteringsperiodeFor(
                aktivitet = Aktivitet(UUID.randomUUID(), Aktivitet.AktivitetsType.Arbeid, "PT7H30M"),
            )

        rapporteringsperiode.arbeidet() shouldBe true
    }

    @Test
    fun `Arbeidet = false true hvis bruker ikke jobbet`() {
        val rapporteringsperiode =
            rapporteringsperiodeFor(
                aktivitet = Aktivitet(UUID.randomUUID(), Aktivitet.AktivitetsType.Fravaer, ""),
            )

        rapporteringsperiode.arbeidet() shouldBe false
    }

    @Test
    fun `kan konvertere til PeriodeData`() {
        val id = 123456789L
        val originalId = 123456788L
        val ident = "01020312345"
        val periode = Periode(LocalDate.now(), LocalDate.now().plusDays(13))
        val aktiviteter = listOf(Aktivitet(UUID.randomUUID(), Aktivitet.AktivitetsType.Utdanning, ""))
        val mottattDato = LocalDate.now()

        val rapporteringsperiode =
            Rapporteringsperiode(
                id = id,
                type = "09",
                periode = periode,
                dager =
                    (0..13)
                        .map { i ->
                            Dag(
                                dato = LocalDate.now().plusDays(i.toLong()),
                                aktiviteter = aktiviteter,
                                dagIndex = i,
                            )
                        },
                kanSendesFra = periode.tilOgMed.minusDays(2),
                sisteFristForTrekk = periode.tilOgMed.plusDays(8),
                kanSendes = true,
                kanEndres = true,
                bruttoBelop = null,
                begrunnelseEndring = "Begrunnelse",
                status = RapporteringsperiodeStatus.TilUtfylling,
                mottattDato = mottattDato,
                registrertArbeidssoker = true,
                originalId = originalId,
                rapporteringstype = "type",
                html = "<html />",
            )

        val periodeData = rapporteringsperiode.toPeriodeData(ident, OpprettetAv.Dagpenger, emptyList())

        periodeData.id shouldBe id
        periodeData.ident shouldBe ident
        periodeData.periode shouldBe periode
        (0..13).onEach {
            periodeData.dager[it].dato shouldBe LocalDate.now().plusDays(it.toLong())
            periodeData.dager[it].aktiviteter shouldBe aktiviteter
            periodeData.dager[it].dagIndex shouldBe it
            periodeData.dager[it].meldt shouldBe false
        }
        periodeData.kanSendesFra shouldBe periode.tilOgMed.minusDays(2)
        periodeData.opprettetAv shouldBe OpprettetAv.Dagpenger
        periodeData.kilde shouldBe Kilde(PeriodeData.Rolle.Bruker, ident)
        periodeData.type shouldBe Type.Korrigert
        periodeData.status shouldBe "TilUtfylling"
        periodeData.innsendtTidspunkt?.truncatedTo(ChronoUnit.MINUTES) shouldBe LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        periodeData.korrigeringAv shouldBe originalId
        periodeData.bruttoBelop shouldBe null
        periodeData.begrunnelseEndring shouldBe "Begrunnelse"
        periodeData.registrertArbeidssoker shouldBe true
    }
}
