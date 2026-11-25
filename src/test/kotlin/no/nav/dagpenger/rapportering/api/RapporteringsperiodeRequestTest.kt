package no.nav.dagpenger.rapportering.api

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.KortType.Ordinaert
import no.nav.dagpenger.rapportering.model.OpprettetAv.Dagpenger
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.model.SporsmalOmRegistrertArbeidssoker
import no.nav.dagpenger.rapportering.model.ÅrsakBrukerHarIkkeSvartOmArbeidssøkerstatus
import no.nav.dagpenger.rapportering.model.ÅrsakBrukerHarIkkeSvartOmArbeidssøkerstatus.KORRIGERT_MELDEKORT
import no.nav.dagpenger.rapportering.utils.UUIDv7.newUuid
import org.junit.jupiter.api.Test
import java.time.LocalDate.now

class RapporteringsperiodeRequestTest {
    private val nå = now()

    @Test
    fun `toRapporteringsperiode bruker verdier fra requesten når de eksister`() {
        val forventetId = newUuid().toString()

        val rapporteringsperiode =
            RapporteringsperiodeRequest(
                id = forventetId,
                dager = listOf(Dag(now(), listOf(), 0)),
                begrunnelseEndring = "Begrunnelse fra requesten",
                registrertArbeidssoker = true,
                rapporteringstype = "Rapporteringstype fra requesten",
                html = "masse html",
            ).toRapporteringsperiode(lagRapporteringsperiode(forventetId))

        with(rapporteringsperiode) {
            id shouldBe forventetId
            dager.size shouldBe 1
            begrunnelseEndring shouldBe "Begrunnelse fra requesten"
            sporsmalOmRegistrertArbeidssoker.svarFraBruker shouldBe true
            rapporteringstype shouldBe "Rapporteringstype fra requesten"
            html shouldBe "masse html"
        }
    }

    @Test
    fun `toRapporteringsperiode bruker verdier fra databasen når de ikke eksisterer i requesten`() {
        val forventetId = newUuid().toString()

        val rapporteringsperiode =
            RapporteringsperiodeRequest(
                id = forventetId,
                dager = listOf(Dag(now(), listOf(), 0)),
                begrunnelseEndring = null,
                registrertArbeidssoker = null,
                rapporteringstype = null,
                html = "masse html",
            ).toRapporteringsperiode(lagRapporteringsperiode(forventetId))

        with(rapporteringsperiode) {
            id shouldBe forventetId
            dager.size shouldBe 1
            begrunnelseEndring shouldBe "Begrunnelse fra databasen"
            sporsmalOmRegistrertArbeidssoker.svarFraBruker shouldBe false
            rapporteringstype shouldBe "Rapporteringstype fra databasen"
            html shouldBe "masse html"
        }
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `toRapporteringsperiode setter svarFraBruker til null hvis det er registrert en årsak til at bruker ikke skal svare på spørsmål om arbeidssøkerstatus`() {
        val forventetId = newUuid().toString()

        val rapporteringsperiode =
            RapporteringsperiodeRequest(
                id = forventetId,
                dager = listOf(Dag(now(), listOf(), 0)),
                begrunnelseEndring = null,
                registrertArbeidssoker = false,
                rapporteringstype = null,
                html = "masse html",
            ).toRapporteringsperiode(lagRapporteringsperiode(forventetId, KORRIGERT_MELDEKORT))

        with(rapporteringsperiode) {
            id shouldBe forventetId
            dager.size shouldBe 1
            begrunnelseEndring shouldBe "Begrunnelse fra databasen"
            sporsmalOmRegistrertArbeidssoker.svarFraBruker shouldBe null
            rapporteringstype shouldBe "Rapporteringstype fra databasen"
            html shouldBe "masse html"
        }
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `toRapporteringsperiode setter svarFraBruker til verdi fra databasen hvis det ikke er registrert en årsak til at bruker ikke skal svare på spørsmål om arbeidssøkerstatus og verdi mangler i request`() {
        val forventetId = newUuid().toString()

        val rapporteringsperiode =
            RapporteringsperiodeRequest(
                id = forventetId,
                dager = listOf(Dag(now(), listOf(), 0)),
                begrunnelseEndring = null,
                registrertArbeidssoker = null,
                rapporteringstype = null,
                html = "masse html",
            ).toRapporteringsperiode(lagRapporteringsperiode(forventetId, null))

        with(rapporteringsperiode) {
            id shouldBe forventetId
            dager.size shouldBe 1
            begrunnelseEndring shouldBe "Begrunnelse fra databasen"
            sporsmalOmRegistrertArbeidssoker.svarFraBruker shouldBe false
            rapporteringstype shouldBe "Rapporteringstype fra databasen"
            html shouldBe "masse html"
        }
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `toRapporteringsperiode setter svarFraBruker til verdi fra requesten hvis det ikke er registrert en årsak til at bruker ikke skal svare på spørsmål om arbeidssøkerstatus og verdien er i requesten`() {
        val forventetId = newUuid().toString()

        val rapporteringsperiode =
            RapporteringsperiodeRequest(
                id = forventetId,
                dager = listOf(Dag(now(), listOf(), 0)),
                begrunnelseEndring = null,
                registrertArbeidssoker = true,
                rapporteringstype = null,
                html = "masse html",
            ).toRapporteringsperiode(lagRapporteringsperiode(forventetId, null))

        with(rapporteringsperiode) {
            id shouldBe forventetId
            dager.size shouldBe 1
            begrunnelseEndring shouldBe "Begrunnelse fra databasen"
            sporsmalOmRegistrertArbeidssoker.svarFraBruker shouldBe true
            rapporteringstype shouldBe "Rapporteringstype fra databasen"
            html shouldBe "masse html"
        }
    }

    fun lagRapporteringsperiode(
        forventetId: String,
        arsakBrukerHarIkkeSvart: ÅrsakBrukerHarIkkeSvartOmArbeidssøkerstatus? = null,
    ): Rapporteringsperiode =
        Rapporteringsperiode(
            id = forventetId,
            type = Ordinaert,
            periode = Periode(nå.minusDays(13), nå),
            dager = listOf(),
            kanSendesFra = now(),
            sisteFristForTrekk = now(),
            kanSendes = true,
            kanEndres = true,
            status = TilUtfylling,
            bruttoBelop = null,
            begrunnelseEndring = "Begrunnelse fra databasen",
            mottattDato = null,
            sporsmalOmRegistrertArbeidssoker = SporsmalOmRegistrertArbeidssoker(false, arsakBrukerHarIkkeSvart),
            opprettetAv = Dagpenger,
            originalId = null,
            rapporteringstype = "Rapporteringstype fra databasen",
        )
}
