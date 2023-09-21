package no.nav.dagpenger.rapportering.api.dto

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.dagpenger.rapportering.Godkjenningsendring
import no.nav.dagpenger.rapportering.Godkjenningslogg
import no.nav.dagpenger.rapportering.Rapporteringsperiode
import no.nav.dagpenger.rapportering.helpers.januar
import no.nav.dagpenger.rapportering.hendelser.AvgodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Aktivitetstidslinje
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class RapporteringsperiodeMapperTest {
    val testIdent = "12345678910"

    @Test
    fun `sisteGodkjent mappes riktig`() {
        val periode = lagRapporteringsperiode(
            fom = 1.januar,
            tom = 14.januar,
            tilstand = Rapporteringsperiode.TilstandType.TilUtfylling,
        )

        // Ikke godkjent
        RapporteringsperiodeMapper(periode).dto.sistGodkjent shouldBe null

        periode.behandle(
            GodkjennPeriodeHendelse(
                ident = testIdent,
                rapporteringId = periode.rapporteringsperiodeId,
            ),
        )

        // Sist godkjent av bruker
        RapporteringsperiodeMapper(periode).dto.also {
            it.sistGodkjent shouldNotBe null
            it.sistGodkjent!!.kilde.kildeType shouldBe "Sluttbruker"
            it.sistGodkjent!!.kilde.id shouldBe testIdent
        }

        periode.behandle(
            AvgodkjennPeriodeHendelse(
                ident = testIdent,
                rapporteringId = periode.rapporteringsperiodeId,
            ),
        )

        RapporteringsperiodeMapper(periode).dto.kanGodkjennesFra shouldBe 13.januar

        // Ikke godkjent
        RapporteringsperiodeMapper(periode).dto.sistGodkjent shouldBe null

        periode.behandle(
            GodkjennPeriodeHendelse(
                ident = testIdent,
                rapporteringId = periode.rapporteringsperiodeId,
                Godkjenningsendring.Saksbehandler("123"),
                begrunnelse = "begrunnelse",
            ),
        )

        // Sist godkjent av saksbehandler
        RapporteringsperiodeMapper(periode).dto.also {
            it.sistGodkjent shouldNotBe null
            it.sistGodkjent!!.kilde.kildeType shouldBe "Saksbehandler"
            it.sistGodkjent!!.kilde.id shouldBe "123"
        }
    }
}

private fun lagRapporteringsperiode(
    fom: LocalDate,
    tom: LocalDate,
    tilstand: Rapporteringsperiode.TilstandType,
    id: UUID = UUID.randomUUID(),
    aktiviteter: List<Aktivitet> = emptyList(),
): Rapporteringsperiode {
    return Rapporteringsperiode.rehydrer(
        rapporteringsperiodeId = id,
        beregnesEtter = tom,
        fraOgMed = fom,
        tilOgMed = tom,
        tilstand = tilstand,
        opprettet = LocalDateTime.now(),
        tidslinje = Aktivitetstidslinje(fom..tom).also {
            aktiviteter.forEach(it::leggTilAktivitet)
        },
        Godkjenningslogg(),
        korrigerer = null,
    )
}
