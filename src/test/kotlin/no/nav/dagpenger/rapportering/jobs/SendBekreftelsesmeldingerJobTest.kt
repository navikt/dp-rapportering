package no.nav.dagpenger.rapportering.jobs

import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.dagpenger.rapportering.api.ApiTestSetup.Companion.setEnvConfig
import no.nav.dagpenger.rapportering.connector.createMockClient
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.KortType
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.repository.BekreftelsesmeldingRepository
import no.nav.dagpenger.rapportering.repository.RapporteringRepository
import no.nav.dagpenger.rapportering.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.utils.MetricsTestUtil.meterRegistry
import no.nav.dagpenger.rapportering.utils.UUIDv7
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SendBekreftelsesmeldingerJobTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            setEnvConfig()
        }
    }

    @Test
    fun `skal sende bekreftelsesmelding`() {
        val ident = "01020312345"

        val uuid1 = UUIDv7.newUuid()
        val rapporteringsperiodeId1 = UUIDv7.newUuid().toString()
        val uuid2 = UUIDv7.newUuid()
        val rapporteringsperiodeId2 = UUIDv7.newUuid().toString()

        val fom = LocalDate.now().minusDays(13)
        val tom = LocalDate.now()
        val rapporteringsperiode2 =
            Rapporteringsperiode(
                id = rapporteringsperiodeId2,
                type = KortType.Ordinaert,
                periode = Periode(fom, tom),
                dager = (0..13).map { Dag(fom.plusDays(it.toLong()), emptyList(), it) },
                kanSendesFra = tom.minusDays(1),
                sisteFristForTrekk = tom.plusWeeks(1),
                kanSendes = true,
                kanEndres = true,
                bruttoBelop = 0.0,
                begrunnelseEndring = null,
                status = RapporteringsperiodeStatus.TilUtfylling,
                mottattDato = tom,
                registrertArbeidssoker = false,
                originalId = null,
                rapporteringstype = null,
            )

        val sendtBekreftelseId = UUIDv7.newUuid()

        val bekreftelsesmeldingRepository = mockk<BekreftelsesmeldingRepository>()
        coEvery {
            bekreftelsesmeldingRepository.hentBekreftelsesmeldingerSomSkalSendes(eq(LocalDate.now()))
        } returns
            listOf(
                Triple(uuid1, rapporteringsperiodeId1, ident),
                Triple(uuid2, rapporteringsperiodeId2, ident),
            )
        coEvery {
            bekreftelsesmeldingRepository.oppdaterBekreftelsesmelding(
                eq(uuid2),
                eq(sendtBekreftelseId),
                any(),
            )
        } returns Unit

        // rapporteringsperiodeId1 eksisterer ikke
        // rapporteringsperiodeId2 eksisterer
        // Ikke-eksisterende perioder skal ikke forhindre at eksisterende perioder sendes
        val rapporteringRepository = mockk<RapporteringRepository>()
        coEvery {
            rapporteringRepository.hentRapporteringsperiode(
                eq(rapporteringsperiodeId1),
                eq(ident),
            )
        } returns null
        coEvery {
            rapporteringRepository.hentRapporteringsperiode(
                eq(rapporteringsperiodeId2),
                eq(ident),
            )
        } returns rapporteringsperiode2

        val arbeidssøkerService = mockk<ArbeidssøkerService>()
        coEvery { arbeidssøkerService.sendBekreftelse(eq(ident), eq(rapporteringsperiode2)) } returns sendtBekreftelseId

        val sendBekreftelsesmeldingerJob =
            SendBekreftelsesmeldingerJob(
                meterRegistry = meterRegistry,
                httpClient = createMockClient(HttpStatusCode.InternalServerError, ""),
                bekreftelsesmeldingRepository = bekreftelsesmeldingRepository,
                rapporteringRepository = rapporteringRepository,
                arbeidssøkerService = arbeidssøkerService,
            )

        val mockedTime = LocalTime.of(1, 59, 58)
        val mockTimeProvider = TimeProvider { LocalDateTime.now().with(mockedTime) }

        val taskExecutor =
            TaskExecutor(
                scheduledTasks =
                    listOf(
                        ScheduledTask(sendBekreftelsesmeldingerJob, 2, 0),
                    ),
                timeProvider = mockTimeProvider,
            )

        taskExecutor.startExecution()

        // sendBekreftelse og oppdaterBekreftelsesmelding må kalles for rapporteringsperiodeId2
        coVerify(exactly = 1, timeout = 5000) {
            arbeidssøkerService.sendBekreftelse(
                eq(ident),
                eq(rapporteringsperiode2),
            )
        }
        coVerify(exactly = 1, timeout = 5000) {
            bekreftelsesmeldingRepository.oppdaterBekreftelsesmelding(
                eq(uuid2),
                eq(sendtBekreftelseId),
                any(),
            )
        }
    }
}
