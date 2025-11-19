package no.nav.dagpenger.rapportering.repository

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import no.nav.dagpenger.rapportering.connector.toKortType
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Arbeid
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Utdanning
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Innsendt
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Midlertidig
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.repository.Postgres.dataSource
import no.nav.dagpenger.rapportering.repository.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.utils.MetricsTestUtil.actionTimer
import no.nav.dagpenger.rapportering.utils.januar
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class RapporteringRepositoryPostgresTest {
    private val rapporteringRepositoryPostgres = RapporteringRepositoryPostgres(dataSource, actionTimer)

    private val ident = "12345678910"

    @Test
    fun `kan hente alle rapporteringsperioder`() {
        withMigratedDb {
            val rapporteringsperiode = rapporteringRepositoryPostgres.hentAlleLagredeRapporteringsperioder()

            rapporteringsperiode.size shouldBe 0
        }
    }

    @Test
    fun `kan hente rapporteringsperiode`() {
        val id = "6269"
        val rapporteringsperiode = getRapporteringsperiode(id = id)

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)
            val hentetRapporteringsperiode = rapporteringRepositoryPostgres.hentRapporteringsperiode(id = id, ident = ident)

            with(hentetRapporteringsperiode) {
                id shouldBe id
                this?.bruttoBelop?.shouldBe(null)
                this?.registrertArbeidssoker?.shouldBe(null)
            }
        }
    }

    @Test
    fun `kan hente rapporteringsperiode opprettet manuelt (etterregistrering)`() {
        val id = "6269"
        val rapporteringsperiode = getRapporteringsperiode(id = id, type = "09")

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)
            val hentetRapporteringsperiode = rapporteringRepositoryPostgres.hentRapporteringsperiode(id = id, ident = ident)

            with(hentetRapporteringsperiode) {
                id shouldBe id
                this?.registrertArbeidssoker?.shouldBe(true)
            }
        }
    }

    @Test
    fun `kan hente rapporteringsperiode med riktig id`() {
        val id = "6269"
        val rapporteringsperiode = getRapporteringsperiode(id = id)

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)
            val hentetRapporteringsperiode = rapporteringRepositoryPostgres.hentRapporteringsperiode(id = id, ident = ident)

            hentetRapporteringsperiode!!.id shouldBe id
        }
    }

    @Test
    fun `Uthenting av rapporteringsperiode som ikke finnes returnerer null`() {
        withMigratedDb {
            rapporteringRepositoryPostgres.hentRapporteringsperiode(id = "123", ident = ident) shouldBe null
        }
    }

    @Test
    fun `kan hente id for alle rapporteringsperioder som er sendt inn`() {
        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(
                rapporteringsperiode = getRapporteringsperiode("1"),
                ident = ident,
            )
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(
                rapporteringsperiode = getRapporteringsperiode(id = "2", status = Innsendt),
                ident = ident,
            )
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(
                rapporteringsperiode = getRapporteringsperiode(id = "3", status = Innsendt),
                ident = ident,
            )
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(
                rapporteringsperiode = getRapporteringsperiode(id = "4", status = Midlertidig),
                ident = ident,
            )

            // Mock LocalDate.now slik at vi kan sette mottattDato tilbake i tid
            val date = LocalDate.now().minusDays(10)
            mockkStatic(LocalDate::class)
            every { LocalDate.now() } returns date

            //
            rapporteringRepositoryPostgres.oppdaterPeriodeEtterInnsending("3", ident, false, false, Innsendt, true)

            unmockkAll()

            with(rapporteringRepositoryPostgres.hentRapporteringsperiodeIdForInnsendtePerioder()) {
                size shouldBe 1
                get(0) shouldBe "3"
            }
        }
    }

    @Test
    fun `kan hente id for alle midlertidige rapporteringsperioder`() {
        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(
                rapporteringsperiode = getRapporteringsperiode("1"),
                ident = ident,
            )
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(
                rapporteringsperiode = getRapporteringsperiode(id = "2", status = Innsendt),
                ident = ident,
            )
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(
                rapporteringsperiode = getRapporteringsperiode(id = "3", status = Midlertidig),
                ident = ident,
            )

            with(rapporteringRepositoryPostgres.hentRapporteringsperiodeIdForMidlertidigePerioder()) {
                size shouldBe 1
                get(0) shouldBe "3"
            }
        }
    }

    @Test
    fun `kan hente id for rapporteringsperioder som er eldre enn siste frist`() {
        withMigratedDb {
            val now = LocalDate.now()
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(
                rapporteringsperiode = getRapporteringsperiode(id = "1", periode = Periode(now.minusDays(13), now)),
                ident = ident,
            )
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(
                rapporteringsperiode = getRapporteringsperiode(id = "2", periode = Periode(now.minusDays(43), now.minusDays(30))),
                ident = ident,
            )
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(
                rapporteringsperiode = getRapporteringsperiode(id = "3", periode = Periode(now.minusDays(21), now.minusDays(8))),
                ident = ident,
            )

            with(rapporteringRepositoryPostgres.hentRapporteringsperiodeIdForPerioderEtterSisteFrist()) {
                size shouldBe 1
                first() shouldBe "2"
            }
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
    fun `finnesRapporteringsperiode returnerer true hvis perioden finnes`() {
        val rapporteringsperiode = getRapporteringsperiode()
        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(
                rapporteringsperiode = rapporteringsperiode,
                ident = ident,
            )

            rapporteringRepositoryPostgres.finnesRapporteringsperiode(rapporteringsperiode.id, ident) shouldBe true
        }
    }

    @Test
    fun `finnesRapporteringsperiode returnerer false hvis perioden ikke finnes`() {
        withMigratedDb {
            rapporteringRepositoryPostgres.finnesRapporteringsperiode("123", "12345678910") shouldBe false
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
            rapporteringRepositoryPostgres.slettOgLagreAktiviteter(rapporteringId = rapporteringsperiode.id, dagId = dagId, dag = dag)

            val result = rapporteringRepositoryPostgres.hentRapporteringsperiode(id = rapporteringsperiode.id, ident = ident)

            with(result!!) {
                id shouldBe rapporteringsperiode.id
                dager.size shouldBe 14
                dager.first().aktiviteter.size shouldBe 2
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
    fun `kan oppdatere begrunnelse for endring`() {
        val rapporteringsperiode = getRapporteringsperiode()

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)
            rapporteringRepositoryPostgres.oppdaterBegrunnelse(
                rapporteringId = rapporteringsperiode.id,
                ident = ident,
                begrunnelse = "Dette er en begrunnelse",
            )
            val oppdatertPeriode = rapporteringRepositoryPostgres.hentRapporteringsperiode(id = rapporteringsperiode.id, ident = ident)!!

            oppdatertPeriode.begrunnelseEndring shouldBe "Dette er en begrunnelse"
        }
    }

    @Test
    fun `kan oppdatere rapporteringstype for periode`() {
        val rapporteringsperiode = getRapporteringsperiode()

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)
            rapporteringRepositoryPostgres.oppdaterRapporteringstype(
                rapporteringId = rapporteringsperiode.id,
                ident = ident,
                rapporteringstype = "harAktivitet",
            )
            val oppdatertPeriode = rapporteringRepositoryPostgres.hentRapporteringsperiode(id = rapporteringsperiode.id, ident = ident)!!

            oppdatertPeriode.rapporteringstype shouldBe "harAktivitet"
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
            rapporteringRepositoryPostgres.slettOgLagreAktiviteter(rapporteringId = rapporteringsperiode.id, dagId = dagId, dag = dag)

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
    fun `kan oppdatere rapporteringsperiode ved innsending`() {
        val rapporteringsperiode = getRapporteringsperiode()

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)
            rapporteringRepositoryPostgres.oppdaterPeriodeEtterInnsending(
                rapporteringId = rapporteringsperiode.id,
                ident = ident,
                status = Innsendt,
                kanSendes = false,
                kanEndres = true,
            )

            with(rapporteringRepositoryPostgres.hentRapporteringsperiode(id = rapporteringsperiode.id, ident = ident)!!) {
                id shouldBe rapporteringsperiode.id
                status shouldBe Innsendt
                kanSendes shouldBe false
                kanEndres shouldBe true
                mottattDato shouldBe LocalDate.now()
            }
        }
    }

    @Test
    fun `kan oppdatere rapporteringsperiode ved innsending uten å oppdatere mottattDato`() {
        val rapporteringsperiode = getRapporteringsperiode()

        withMigratedDb {
            rapporteringRepositoryPostgres.lagreRapporteringsperiodeOgDager(rapporteringsperiode = rapporteringsperiode, ident = ident)
            rapporteringRepositoryPostgres.oppdaterPeriodeEtterInnsending(
                rapporteringId = rapporteringsperiode.id,
                ident = ident,
                status = Innsendt,
                kanSendes = false,
                kanEndres = true,
                oppdaterMottattDato = false,
            )

            with(rapporteringRepositoryPostgres.hentRapporteringsperiode(id = rapporteringsperiode.id, ident = ident)!!) {
                id shouldBe rapporteringsperiode.id
                status shouldBe Innsendt
                kanSendes shouldBe false
                kanEndres shouldBe true
                mottattDato shouldBe null
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
            rapporteringRepositoryPostgres.slettOgLagreAktiviteter(rapporteringId = rapporteringsperiode.id, dagId = dagId, dag = dag)
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

            rapporteringRepositoryPostgres.slettAktiviteter(dagId)
            val resultatMedEnAktivitet =
                rapporteringRepositoryPostgres.hentRapporteringsperiode(
                    id = rapporteringsperiode.id,
                    ident = ident,
                )

            with(resultatMedEnAktivitet!!) {
                id shouldBe rapporteringsperiode.id
                dager.size shouldBe 14
                dager.first().aktiviteter.size shouldBe 0
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
            rapporteringRepositoryPostgres.hentKanSendes("123") shouldBe null
        }
    }

    @Test
    fun `hentKanSendes returnerer null hvis perioden ikke finnes`() {
        withMigratedDb {
            rapporteringRepositoryPostgres.hentAntallRapporteringsperioder() shouldBe 0
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
    id: String = "6269",
    type: String = "05",
    periode: Periode = Periode(fraOgMed = 1.januar, tilOgMed = 14.januar),
    dager: List<Dag> = getDager(),
    kanSendesFra: LocalDate = 13.januar,
    kanSendes: Boolean = true,
    kanEndres: Boolean = false,
    bruttoBelop: Double? = null,
    status: RapporteringsperiodeStatus = TilUtfylling,
    registrertArbeidssoker: Boolean? = null,
    begrunnelseEndring: String? = null,
    mottattDato: LocalDate? = null,
) = Rapporteringsperiode(
    id = id,
    type = type.toKortType(),
    periode = periode,
    dager = dager,
    kanSendesFra = kanSendesFra,
    kanSendes = kanSendes,
    kanEndres = kanEndres,
    bruttoBelop = bruttoBelop,
    status = status,
    registrertArbeidssoker = registrertArbeidssoker,
    begrunnelseEndring = begrunnelseEndring,
    originalId = null,
    rapporteringstype = null,
    mottattDato = mottattDato,
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
