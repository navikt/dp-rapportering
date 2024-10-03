package no.nav.dagpenger.rapportering.repository

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.repository.Postgres.dataSource
import no.nav.dagpenger.rapportering.repository.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.utils.MetricsTestUtil.actionTimer
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException

class InnsendingtidspunktRepositoryPostgresTest {
    val innsendingtidspunktRepositoryPostgres = InnsendingtidspunktRepositoryPostgres(dataSource, actionTimer)

    @Test
    fun `hentInnsendingtidspunkt should return null when periodeKode is not found`() {
        val periodeKode = "202440"
        withMigratedDb {
            val innsendingtidspunkt = innsendingtidspunktRepositoryPostgres.hentInnsendingtidspunkt(periodeKode)
            innsendingtidspunkt shouldBe null
        }
    }

    @Test
    fun `hentInnsendingtidspunkt should return value when periodeKode is found`() {
        val periodeKode = "202440"
        val verdi = -5
        withMigratedDb {
            lagreInnsendingtidspunkt(periodeKode, verdi)
            val innsendingtidspunkt = innsendingtidspunktRepositoryPostgres.hentInnsendingtidspunkt(periodeKode)
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

    private fun lagreInnsendingtidspunkt(
        periodeKode: String,
        verdi: Int,
    ) {
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "INSERT INTO INNSENDINGTIDSPUNKT (PERIODE_KODE, VERDI) VALUES (?, ?)",
                    periodeKode,
                    verdi,
                ).asUpdate,
            )
        }
    }
}
