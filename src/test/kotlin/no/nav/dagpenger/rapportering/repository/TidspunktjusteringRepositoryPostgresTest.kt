package no.nav.dagpenger.rapportering.repository

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.dagpenger.rapportering.repository.Postgres.dataSource
import no.nav.dagpenger.rapportering.repository.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.utils.MetricsTestUtil.actionTimer
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException

class TidspunktjusteringRepositoryPostgresTest {
    val tidspunktjusteringRepositoryPostgres = TidspunktjusteringRepositoryPostgres(dataSource, actionTimer)

    @Test
    fun `hentInnsendingtidspunkt should return null when periodeKode is not found`() {
        val periodeKode = "202440"
        withMigratedDb {
            val innsendingtidspunkt = tidspunktjusteringRepositoryPostgres.hentInnsendingtidspunkt(periodeKode)
            innsendingtidspunkt shouldBe null
        }
    }

    @Test
    fun `hentInnsendingtidspunkt should return value when periodeKode is found`() {
        val periodeKode = "202440"
        val verdi = -5
        withMigratedDb {
            lagreInnsendingtidspunkt(periodeKode, verdi)
            val innsendingtidspunkt = tidspunktjusteringRepositoryPostgres.hentInnsendingtidspunkt(periodeKode)
            innsendingtidspunkt shouldBe verdi
        }
    }

    @Test
    fun `periodeKode kan kun inneholde 6 tegn`() {
        val periodeKode = "2024407"
        val verdi = -5
        withMigratedDb {
            shouldThrow<PSQLException> {
                lagreInnsendingtidspunkt(periodeKode, verdi)
            }
        }
    }

    @Test
    fun `hentSisteFristForTrekkJustering should return null when periodeKode is not found`() {
        val periodeKode = "202440"
        withMigratedDb {
            val innsendingtidspunkt = tidspunktjusteringRepositoryPostgres.hentSisteFristForTrekkJustering(periodeKode)
            innsendingtidspunkt shouldBe null
        }
    }

    @Test
    fun `hentSisteFristForTrekkJustering should return value when periodeKode is found`() {
        val periodeKode = "202440"
        val verdi = -5
        withMigratedDb {
            lagreSisteFristForTrekkJustering(periodeKode, verdi)
            val innsendingtidspunkt = tidspunktjusteringRepositoryPostgres.hentSisteFristForTrekkJustering(periodeKode)
            innsendingtidspunkt shouldBe verdi
        }
    }

    @Test
    fun `periodeKode for sisteFristForTrekk kan kun inneholde 6 tegn`() {
        val periodeKode = "2024407"
        val verdi = -5
        withMigratedDb {
            shouldThrow<PSQLException> {
                lagreSisteFristForTrekkJustering(periodeKode, verdi)
            }
        }
    }

    private fun lagreInnsendingtidspunkt(
        periodeKode: String,
        verdi: Int,
    ) {
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "INSERT INTO INNSENDINGTIDSPUNKT (PERIODE_KODE, VERDI) VALUES (?, ?)",
                    periodeKode,
                    verdi,
                ).asUpdate,
            )
        }
    }

    private fun lagreSisteFristForTrekkJustering(
        periodeKode: String,
        verdi: Int,
    ) {
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "INSERT INTO siste_frist_for_trekk_justeringer (periode_kode, verdi) VALUES (?, ?)",
                    periodeKode,
                    verdi,
                ).asUpdate,
            )
        }
    }
}
