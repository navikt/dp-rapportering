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
import java.sql.BatchUpdateException
import java.time.LocalDate
import java.util.UUID

class RapporteringRepositoryPostgresTest {
    val rapporteringRepositoryPostgres = RapporteringRepositoryPostgres(dataSource)

    val ident = "12345678910"

    @Test
    fun `kan hente alle rapporteringsperioder`() {
        withMigratedDb {
            val rapporteringsperiode = rapporteringRepositoryPostgres.hentAlleLagredeRapporteringsperioder()

            rapporteringsperiode.size shouldBe 0
        }
    }

    @Test
    fun `kan hente rapporteringsperiode`() {
        val id = 6269L
        val rapporteringsperiode = getRapporteringsperiode(id = id)

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

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)
            val rapporteringsperiode = rapporteringRepositoryPostgres.hentRapporteringsperiode(id = id, ident = ident)

            rapporteringsperiode!!.id shouldBe id
        }
    }

    @Test
    fun `Uthenting av rapporteringsperiode som ikke finnes returnerer null`() {
        withMigratedDb {
            rapporteringRepositoryPostgres.hentRapporteringsperiode(id = 123L, ident = ident) shouldBe null
        }
    }

    @Test
    fun `kan lagre rapporteringsperiode`() {
        val rapporteringsperiode = getRapporteringsperiode()
        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(
                rapporteringsperiode = rapporteringsperiode,
                ident = ident,
            )

            rapporteringRepositoryPostgres.hentAlleLagredeRapporteringsperioder().size shouldBe 1
        }
    }

    @Test
    fun `kan lagre rapporteringsperiode med aktiviteter`() {
        val rapporteringsperiode =
            getRapporteringsperiode(dager = getDager(aktivitet = Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null)))
        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(
                rapporteringsperiode = rapporteringsperiode,
                ident = ident,
            )

            val periodeFraDb = rapporteringRepositoryPostgres.hentAlleLagredeRapporteringsperioder()

            periodeFraDb.size shouldBe 1
            with(periodeFraDb.first()) {
                id shouldBe rapporteringsperiode.id
                dager.size shouldBe 14
                dager.forEach { dag ->
                    dag.aktiviteter.size shouldBe 1
                    dag.aktiviteter.first().type shouldBe Utdanning
                }
            }
        }
    }

    @Test
    fun `lagre eksisterende rapporteringsperiode kaster exception`() {
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
                ident = ident,
            )

            val lagretRapportering = rapporteringRepositoryPostgres.hentAlleLagredeRapporteringsperioder()

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
                        Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null),
                        Aktivitet(id = UUID.randomUUID(), type = Arbeid, timer = "PT5H30M"),
                    ),
                dagIndex = 0,
            )

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)

            val dagId = rapporteringRepositoryPostgres.hentDagId(rapporteringId = rapporteringsperiode.id, dagIdex = dag.dagIndex)
            rapporteringRepositoryPostgres.lagreAktiviteter(rapporteringId = rapporteringsperiode.id, dagId = dagId, dag = dag)

            val result = rapporteringRepositoryPostgres.hentRapporteringsperiode(id = rapporteringsperiode.id, ident = ident)

            with(result!!) {
                id shouldBe rapporteringsperiode.id
                dager.size shouldBe 14
                dager.first().aktiviteter.size shouldBe 2
            }
        }
    }

    @Test
    fun `lagring av aktivitet som allerede aksisterer feiler`() {
        val rapporteringsperiode = getRapporteringsperiode()
        val dag =
            Dag(
                dato = 1.januar,
                aktiviteter = listOf(Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null)),
                dagIndex = 0,
            )

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)

            val dagId = rapporteringRepositoryPostgres.hentDagId(rapporteringId = rapporteringsperiode.id, dagIdex = dag.dagIndex)
            rapporteringRepositoryPostgres.lagreAktiviteter(rapporteringId = rapporteringsperiode.id, dagId = dagId, dag = dag)

            shouldThrow<BatchUpdateException> {
                rapporteringRepositoryPostgres.lagreAktiviteter(rapporteringId = rapporteringsperiode.id, dagId = dagId, dag = dag)
            }

            val result = rapporteringRepositoryPostgres.hentRapporteringsperiode(id = rapporteringsperiode.id, ident = ident)

            with(result!!) {
                id shouldBe rapporteringsperiode.id
                dager.size shouldBe 14
                dager.first().aktiviteter.size shouldBe 1
            }
        }
    }

    @Test
    fun `kan oppdatere om bruker vil fortsette som registrert arbeidssoker`() {
        val rapporteringsperiode = getRapporteringsperiode()

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)
            rapporteringRepositoryPostgres.oppdaterRegistrertArbeidssoker(
                rapporteringId = rapporteringsperiode.id,
                ident = ident,
                registrertArbeidssoker = true,
            )
            val oppdatertPeriode = rapporteringRepositoryPostgres.hentRapporteringsperiode(id = rapporteringsperiode.id, ident = ident)!!

            oppdatertPeriode.registrertArbeidssoker shouldBe true
        }
    }

    @Test
    fun `kan oppdatere rapporteringsperiode fra adapter`() {
        val rapporteringsperiode = getRapporteringsperiode()
        val dag =
            Dag(
                dato = 1.januar,
                aktiviteter =
                    listOf(
                        Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null),
                        Aktivitet(id = UUID.randomUUID(), type = Arbeid, timer = "PT5H30M"),
                    ),
                dagIndex = 0,
            )

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)

            val dagId = rapporteringRepositoryPostgres.hentDagId(rapporteringId = rapporteringsperiode.id, dagIdex = dag.dagIndex)
            rapporteringRepositoryPostgres.lagreAktiviteter(rapporteringId = rapporteringsperiode.id, dagId = dagId, dag = dag)

            val lagretRapporteringsperiode =
                rapporteringRepositoryPostgres.hentRapporteringsperiode(
                    id = rapporteringsperiode.id,
                    ident = ident,
                )

            with(lagretRapporteringsperiode!!) {
                id shouldBe rapporteringsperiode.id
                dager.size shouldBe 14
                dager.first().aktiviteter.size shouldBe 2
                kanSendes shouldBe true
                kanEndres shouldBe false
                bruttoBelop shouldBe null
                status shouldBe TilUtfylling
            }

            rapporteringRepositoryPostgres.oppdaterRapporteringsperiodeFraArena(
                rapporteringsperiode.copy(
                    kanSendes = false,
                    kanEndres = true,
                    bruttoBelop = 100.0,
                    status = Innsendt,
                ),
                ident,
            )

            val oppdatertRapporteringsperiode =
                rapporteringRepositoryPostgres.hentRapporteringsperiode(
                    id = rapporteringsperiode.id,
                    ident = ident,
                )

            with(oppdatertRapporteringsperiode!!) {
                id shouldBe rapporteringsperiode.id
                dager.size shouldBe 14
                dager.first().aktiviteter.size shouldBe 2
                kanSendes shouldBe false
                kanEndres shouldBe true
                bruttoBelop shouldBe 100.0
                status shouldBe Innsendt
            }
        }
    }

    @Test
    fun `kan oppdatere status for rapporteringsperiode`() {
        val rapporteringsperiode = getRapporteringsperiode()

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)
            rapporteringRepositoryPostgres.oppdaterRapporteringStatus(
                rapporteringId = rapporteringsperiode.id,
                ident = ident,
                status = Innsendt,
            )

            with(rapporteringRepositoryPostgres.hentRapporteringsperiode(id = rapporteringsperiode.id, ident = ident)!!) {
                id shouldBe rapporteringsperiode.id
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
                        Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null),
                        Aktivitet(id = UUID.randomUUID(), type = Arbeid, timer = "PT5H30M"),
                    ),
                dagIndex = 0,
            )

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)
            val dagId = rapporteringRepositoryPostgres.hentDagId(rapporteringId = rapporteringsperiode.id, dagIdex = dag.dagIndex)
            rapporteringRepositoryPostgres.lagreAktiviteter(rapporteringId = rapporteringsperiode.id, dagId = dagId, dag = dag)
            val resultatMedToAktiviteter =
                rapporteringRepositoryPostgres.hentRapporteringsperiode(
                    id = rapporteringsperiode.id,
                    ident = ident,
                )

            with(resultatMedToAktiviteter!!) {
                id shouldBe rapporteringsperiode.id
                dager.size shouldBe 14
                dager.first().aktiviteter.size shouldBe 2
                dager
                    .first()
                    .aktiviteter
                    .first()
                    .id shouldBe dag.aktiviteter.first().id
                dager
                    .first()
                    .aktiviteter
                    .last()
                    .id shouldBe dag.aktiviteter.last().id
            }

            rapporteringRepositoryPostgres.slettAktiviteter(aktivitetIdListe = listOf(dag.aktiviteter.first().id))
            val resultatMedEnAktivitet =
                rapporteringRepositoryPostgres.hentRapporteringsperiode(
                    id = rapporteringsperiode.id,
                    ident = ident,
                )

            with(resultatMedEnAktivitet!!) {
                id shouldBe rapporteringsperiode.id
                dager.size shouldBe 14
                dager.first().aktiviteter.size shouldBe 1
                dager
                    .first()
                    .aktiviteter
                    .first()
                    .id shouldBe dag.aktiviteter.last().id
            }
        }
    }

    @Test
    fun `slett av ikke-eksisterende aktivitet kaster exception`() {
        withMigratedDb {
            shouldThrow<RuntimeException> {
                rapporteringRepositoryPostgres.slettAktiviteter(aktivitetIdListe = listOf(UUID.randomUUID()))
            }
        }
    }

    @Test
    fun `kan slette rapporteringsperiode`() {
        val rapporteringsperiode = getRapporteringsperiode()

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(
                rapporteringsperiode = rapporteringsperiode,
                ident = ident,
            )
            rapporteringRepositoryPostgres.slettRaporteringsperiode(rapporteringsperiode.id)

            rapporteringRepositoryPostgres.hentRapporteringsperiode(id = rapporteringsperiode.id, ident = ident) shouldBe null
        }
    }

    @Test
    fun `sletting av rapporteringsperiode som ikke finnes kaster RuntimeException`() {
        withMigratedDb {
            shouldThrow<RuntimeException> {
                rapporteringRepositoryPostgres.slettRaporteringsperiode(123L)
            }
        }
    }

    @Test
    fun `hentAntallRapporteringsperioder returnerer 0 når databasen er tom`() {
        withMigratedDb {
            rapporteringRepositoryPostgres.hentAntallRapporteringsperioder() shouldBe 0
        }
    }

    @Test
    fun `hentAntallRapporteringsperioder gir riktig antall`() {
        val rapporteringsperiode = getRapporteringsperiode()
        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)
            rapporteringRepositoryPostgres.hentAntallRapporteringsperioder() shouldBe 1
        }
    }
}

fun getRapporteringsperiode(
    id: Long = 6269L,
    periode: Periode = Periode(fraOgMed = 1.januar, tilOgMed = 14.januar),
    dager: List<Dag> = getDager(),
    kanSendesFra: LocalDate = 13.januar,
    kanSendes: Boolean = true,
    kanEndres: Boolean = false,
    bruttoBelop: Double? = null,
    status: RapporteringsperiodeStatus = TilUtfylling,
    registrertArbeidssoker: Boolean? = null,
    begrunnelseEndring: String? = null,
) = Rapporteringsperiode(
    id = id,
    periode = periode,
    dager = dager,
    kanSendesFra = kanSendesFra,
    kanSendes = kanSendes,
    kanEndres = kanEndres,
    bruttoBelop = bruttoBelop,
    status = status,
    registrertArbeidssoker = registrertArbeidssoker,
    begrunnelseEndring = begrunnelseEndring,
)

private fun getDager(
    startDato: LocalDate = 1.januar,
    aktivitet: Aktivitet? = null,
): List<Dag> =
    (0..13)
        .map { i ->
            Dag(
                dato = startDato.plusDays(i.toLong()),
                aktiviteter = aktivitet?.let { listOf(it.copy(id = UUID.randomUUID())) } ?: emptyList(),
                dagIndex = i,
            )
        }
