package no.nav.dagpenger.rapportering.repository

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Arbeid
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Utdanning
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Innsendt
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.repository.Postgres.dataSource
import no.nav.dagpenger.rapportering.repository.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.utils.januar
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

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
        val rapporteringsperiode = getRapporteringsperiode(id = id)

        val ident = "12345678910"

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)
            val rapporteringsperiode = rapporteringRepositoryPostgres.hentRapporteringsperiode(id = id, ident = ident)

            with(rapporteringsperiode) {
                id shouldBe id
                this?.bruttoBelop?.shouldBe(null)
                this?.registrertArbeidssoker?.shouldBe(null)
            }
        }
    }

    @Test
    fun `kan hente rapporteringsperiode med riktig id`() {
        val id = 6269L
        val rapporteringsperiode = getRapporteringsperiode(id = id)

        val ident = "12345678910"

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)
            val rapporteringsperiode = rapporteringRepositoryPostgres.hentRapporteringsperiode(id = id, ident = ident)

            rapporteringsperiode!!.id shouldBe id
        }
    }

    @Test
    fun `Uthenting av rapporteringsperiode som ikke finnes returnerer null`() {
        withMigratedDb {
            rapporteringRepositoryPostgres.hentRapporteringsperiode(123L, "12345678910") shouldBe null
        }
    }

    @Test
    fun `kan lagre rapporteringsperiode`() {
        val rapporteringsperiode = getRapporteringsperiode()
        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(
                rapporteringsperiode = rapporteringsperiode,
                ident = "12345678910",
            )

            rapporteringRepositoryPostgres.hentRapporteringsperioder().size shouldBe 1
        }
    }

    @Test
    fun `lagre eksisterende rapporteringsperiode kaster exception`() {
        val ident = "12345678910"
        val rapporteringsperiode = getRapporteringsperiode()
        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)

            shouldThrow<RuntimeException> {
                rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)
            }
        }
    }

    @Test
    fun `kan oppdaterer lagret rapporteringsperiode`() {
        val rapporteringsperiode = getRapporteringsperiode()
        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(
                rapporteringsperiode = rapporteringsperiode,
                ident = "12345678910",
            )

            val lagretRapportering = rapporteringRepositoryPostgres.hentRapporteringsperioder()

            lagretRapportering.size shouldBe 1
        }
    }

    @Test
    fun `kan lagre aktiviteter`() {
        val rapporteringsperiode = getRapporteringsperiode()
        val dag =
            Dag(
                dato = 1.januar,
                aktiviteter =
                    listOf(
                        Aktivitet(uuid = UUID.randomUUID(), type = Utdanning, timer = null),
                        Aktivitet(uuid = UUID.randomUUID(), type = Arbeid, timer = "PT5H30M"),
                    ),
                dagIndex = 0,
            )
        val ident = "12345678910"

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode, "12345678910")

            rapporteringRepositoryPostgres.lagreAktiviteter(rapporteringsperiode.id, dag)

            val result = rapporteringRepositoryPostgres.hentRapporteringsperiode(rapporteringsperiode.id, ident)

            with(result!!) {
                id shouldBe rapporteringsperiode.id
                dager.size shouldBe 14
                dager.first().aktiviteter.size shouldBe 2
            }
        }
    }

    @Test
    fun `lagring av aktivitet som allerede aksisterer feiler ikke`() {
        val rapporteringsperiode = getRapporteringsperiode()
        val dag =
            Dag(
                dato = 1.januar,
                aktiviteter = listOf(Aktivitet(uuid = UUID.randomUUID(), type = Utdanning, timer = null)),
                dagIndex = 0,
            )
        val ident = "12345678910"

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode, "12345678910")

            rapporteringRepositoryPostgres.lagreAktiviteter(rapporteringsperiode.id, dag)
            rapporteringRepositoryPostgres.lagreAktiviteter(rapporteringsperiode.id, dag)

            val result = rapporteringRepositoryPostgres.hentRapporteringsperiode(rapporteringsperiode.id, ident)

            with(result!!) {
                id shouldBe rapporteringsperiode.id
                dager.size shouldBe 14
                dager.first().aktiviteter.size shouldBe 1
            }
        }
    }

    @Test
    fun `kan oppdatere rapporteringsperiode fra adapter`() {
        val rapporteringsperiode = getRapporteringsperiode()
        val dag =
            Dag(
                dato = 1.januar,
                aktiviteter = listOf(Aktivitet(uuid = UUID.randomUUID(), type = Utdanning, timer = null)),
                dagIndex = 0,
            )
        val ident = "12345678910"

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode, "12345678910")

            rapporteringRepositoryPostgres.lagreAktiviteter(rapporteringsperiode.id, dag)
            rapporteringRepositoryPostgres.lagreAktiviteter(rapporteringsperiode.id, dag)

            val lagretRapporteringsperiode = rapporteringRepositoryPostgres.hentRapporteringsperiode(rapporteringsperiode.id, ident)

            with(lagretRapporteringsperiode!!) {
                id shouldBe rapporteringsperiode.id
                dager.size shouldBe 14
                dager.first().aktiviteter.size shouldBe 1
                kanSendes shouldBe true
                kanKorrigeres shouldBe false
                bruttoBelop shouldBe null
                status shouldBe TilUtfylling
            }

            rapporteringRepositoryPostgres.oppdaterRapporteringsperiodeFraArena(
                rapporteringsperiode.copy(
                    kanSendes = false,
                    kanKorrigeres = true,
                    bruttoBelop = 100.0,
                    status = Innsendt,
                ),
                ident,
            )

            val oppdatertRapporteringsperiode = rapporteringRepositoryPostgres.hentRapporteringsperiode(rapporteringsperiode.id, ident)

            with(oppdatertRapporteringsperiode!!) {
                id shouldBe rapporteringsperiode.id
                dager.size shouldBe 14
                dager.first().aktiviteter.size shouldBe 1
                kanSendes shouldBe false
                kanKorrigeres shouldBe true
                bruttoBelop shouldBe 100.0
                status shouldBe Innsendt
            }
        }
    }

    @Test
    fun `kan slette aktiviteter`() {
        val rapporteringsperiode = getRapporteringsperiode()
        val dag =
            Dag(
                dato = 1.januar,
                aktiviteter =
                    listOf(
                        Aktivitet(uuid = UUID.randomUUID(), type = Utdanning, timer = null),
                        Aktivitet(uuid = UUID.randomUUID(), type = Arbeid, timer = "PT5H30M"),
                    ),
                dagIndex = 0,
            )
        val ident = "12345678910"

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode, "12345678910")
            rapporteringRepositoryPostgres.lagreAktiviteter(rapporteringsperiode.id, dag)
            val resultatMedToAktiviteter = rapporteringRepositoryPostgres.hentRapporteringsperiode(rapporteringsperiode.id, ident)

            with(resultatMedToAktiviteter!!) {
                id shouldBe rapporteringsperiode.id
                dager.size shouldBe 14
                dager.first().aktiviteter.size shouldBe 2
                dager.first().aktiviteter.first().uuid shouldBe dag.aktiviteter.first().uuid
                dager.first().aktiviteter.last().uuid shouldBe dag.aktiviteter.last().uuid
            }

            val rowsAffected = rapporteringRepositoryPostgres.slettAktivitet(dag.aktiviteter.first().uuid)
            val resultatMedEnAktivitet = rapporteringRepositoryPostgres.hentRapporteringsperiode(rapporteringsperiode.id, ident)

            rowsAffected shouldBe 1
            with(resultatMedEnAktivitet!!) {
                id shouldBe rapporteringsperiode.id
                dager.size shouldBe 14
                dager.first().aktiviteter.size shouldBe 1
                dager.first().aktiviteter.first().uuid shouldBe dag.aktiviteter.last().uuid
            }
        }
    }

    @Test
    fun `slett av ikke-eksisterende aktivitet`() {
        withMigratedDb {
            val rowsAffected = rapporteringRepositoryPostgres.slettAktivitet(UUID.randomUUID())
            rowsAffected shouldBe 0
        }
    }
}

fun getRapporteringsperiode(
    id: Long = 6269L,
    periode: Periode = Periode(fraOgMed = 1.januar, tilOgMed = 14.januar),
    dager: List<Dag> = getDager(),
    kanSendesFra: LocalDate = 13.januar,
    kanSendes: Boolean = true,
    kanKorrigeres: Boolean = false,
    bruttoBelop: Double? = null,
    status: RapporteringsperiodeStatus = TilUtfylling,
    registrertArbeidssoker: Boolean? = null,
) = Rapporteringsperiode(
    id = id,
    periode = periode,
    dager = dager,
    kanSendesFra = kanSendesFra,
    kanSendes = kanSendes,
    kanKorrigeres = kanKorrigeres,
    bruttoBelop = bruttoBelop,
    status = status,
    registrertArbeidssoker = registrertArbeidssoker,
)

fun getDager(startDato: LocalDate = 1.januar): List<Dag> =
    (0..13)
        .map { i ->
            Dag(
                dato = startDato.plusDays(i.toLong()),
                aktiviteter = listOf(),
                dagIndex = i,
            )
        }
