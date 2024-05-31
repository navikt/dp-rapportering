package no.nav.dagpenger.rapportering.repository

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.repository.Postgres.dataSource
import no.nav.dagpenger.rapportering.repository.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.utils.januar
import org.junit.jupiter.api.Test

class RapporteringRepositoryPostgresTest {
    val rapporteringRepositoryPostgres = RapporteringRepositoryPostgres(dataSource)

    @Test
    fun `kan hente alle rapporteringsperioder`() {
        withMigratedDb {
            val rapporteringsperiode = rapporteringRepositoryPostgres.hentRapporteringsperioder()

            rapporteringsperiode.size shouldBe 0
        }
    }

    @Test
    fun `kan hente rapporteringsperiode`() {
        val id = 6269L
        val rapporteringsperiode =
            Rapporteringsperiode(
                id = id,
                periode = Periode(fraOgMed = 1.januar, tilOgMed = 14.januar),
                dager = emptyList(),
                kanSendesFra = 13.januar,
                kanSendes = false,
                kanKorrigeres = false,
                bruttoBelop = null,
                status = TilUtfylling,
                registrertArbeidssoker = null,
            )

        val ident = "12345678910"

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiode(rapporteringsperiode = rapporteringsperiode, ident = ident)
            val rapporteringsperiode = rapporteringRepositoryPostgres.hentRapporteringsperiode(id = id, ident = ident)

            with(rapporteringsperiode) {
                id shouldBe id
                this?.bruttoBelop?.shouldBe(null)
                this?.registrertArbeidssoker?.shouldBe(null)
            }
        }
    }

    @Test
    fun `kan hente rapporteringsperiode med rikti`() {
        val id = 6269L
        val rapporteringsperiode =
            Rapporteringsperiode(
                id = id,
                periode = Periode(fraOgMed = 1.januar, tilOgMed = 14.januar),
                dager = emptyList(),
                kanSendesFra = 13.januar,
                kanSendes = false,
                kanKorrigeres = false,
                bruttoBelop = null,
                status = TilUtfylling,
                registrertArbeidssoker = null,
            )

        val ident = "12345678910"

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiode(rapporteringsperiode = rapporteringsperiode, ident = ident)
            val rapporteringsperiode = rapporteringRepositoryPostgres.hentRapporteringsperiode(id = id, ident = ident)

            with(rapporteringsperiode) {
                id shouldBe id
            }
        }
    }

    @Test
    fun `kan lagre rapporteringsperiode`() {
        val rapporteringsperiode =
            Rapporteringsperiode(
                id = 6269,
                periode = Periode(fraOgMed = 1.januar, tilOgMed = 14.januar),
                dager = emptyList(),
                kanSendesFra = 13.januar,
                kanSendes = false,
                kanKorrigeres = false,
                bruttoBelop = null,
                status = TilUtfylling,
                registrertArbeidssoker = null,
            )
        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiode(rapporteringsperiode = rapporteringsperiode, ident = "12345678910")

            rapporteringRepositoryPostgres.hentRapporteringsperioder().size shouldBe 1
        }
    }
}
