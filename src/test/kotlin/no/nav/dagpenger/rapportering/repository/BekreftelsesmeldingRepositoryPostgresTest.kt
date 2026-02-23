package no.nav.dagpenger.rapportering.repository

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.repository.Postgres.dataSource
import no.nav.dagpenger.rapportering.repository.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.utils.MetricsTestUtil.actionTimer
import no.nav.dagpenger.rapportering.utils.UUIDv7
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BekreftelsesmeldingRepositoryPostgresTest {
    val idag = LocalDate.now()
    val rapporteringsperiodeId = UUIDv7.newUuid().toString()
    val ident = "0102031234"
    val bekreftelsesmeldingRepositoryPostgres = BekreftelsesmeldingRepositoryPostgres(dataSource, actionTimer)

    @Test
    fun `kan lagre, hente og oppdatere data`() {
        withMigratedDb {
            // Hent (vi skal ikke få data)
            var lagretData = bekreftelsesmeldingRepositoryPostgres.hentBekreftelsesmeldingerSomSkalSendes(idag)
            lagretData.size shouldBe 0

            // Lagre
            bekreftelsesmeldingRepositoryPostgres.lagreBekreftelsesmelding(
                rapporteringsperiodeId,
                ident,
                idag,
            )

            // Prøver å lagre en gang til. Skal ikke kaste exception
            bekreftelsesmeldingRepositoryPostgres.lagreBekreftelsesmelding(
                rapporteringsperiodeId,
                ident,
                idag,
            )

            // Hent (nå skal vi få data)
            lagretData = bekreftelsesmeldingRepositoryPostgres.hentBekreftelsesmeldingerSomSkalSendes(idag)
            lagretData.size shouldBe 1
            lagretData[0].second shouldBe rapporteringsperiodeId
            lagretData[0].third shouldBe ident

            val id = lagretData[0].first

            // Oppdater
            bekreftelsesmeldingRepositoryPostgres.oppdaterBekreftelsesmelding(id, UUIDv7.newUuid(), LocalDateTime.now())

            // Hent (vi skal ikke få data)
            lagretData = bekreftelsesmeldingRepositoryPostgres.hentBekreftelsesmeldingerSomSkalSendes(idag)
            lagretData.size shouldBe 0
        }
    }
}
