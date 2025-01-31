package no.nav.dagpenger.rapportering.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import no.nav.dagpenger.rapportering.api.rapporteringsperiodeFor
import no.nav.dagpenger.rapportering.connector.createMockClient
import no.nav.dagpenger.rapportering.kafka.KafkaProdusent
import no.nav.dagpenger.rapportering.model.ArbeidssøkerBekreftelse
import no.nav.dagpenger.rapportering.model.BekreftelsesLøsning
import no.nav.dagpenger.rapportering.repository.Postgres.database
import no.nav.dagpenger.rapportering.utils.tilMillis
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ArbeidssøkerServiceTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            System.setProperty(
                "DB_JDBC_URL",
                "${database.jdbcUrl}&user=${database.username}&password=${database.password}",
            )
            System.setProperty(
                "ARBEIDSSOKERREGISTER_RECORD_KEY_URL",
                "http://arbeidssokerregister_record_key_url/api/v1/record-key",
            )
        }
    }

    @Test
    fun `Kan sende bekreftelse med vilFortsetteSomArbeidssoeker = true`() {
        val ident = "01020312345"
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = true)

        val kallLoggService = mockk<KallLoggService>()
        every { kallLoggService.lagreKafkaUtKallLogg(any()) } returns 1
        every { kallLoggService.lagreRequest(eq(1), any()) } just runs
        every { kallLoggService.lagreResponse(eq(1), eq(200), eq("")) } just runs

        val recordKeyTokenProvider = {
            "TOKEN"
        }

        val bekreftelseKafkaProdusent = mockk<KafkaProdusent<ArbeidssøkerBekreftelse>>()
        val slot = slot<ArbeidssøkerBekreftelse>()
        every { bekreftelseKafkaProdusent.send(any(), capture(slot)) } just runs

        val arbeidssøkerService =
            ArbeidssøkerService(
                kallLoggService,
                recordKeyTokenProvider = recordKeyTokenProvider,
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
                httpClient = createMockClient(200, "{ \"key\": 123456789 }"),
            )

        arbeidssøkerService.sendBekreftelse(ident, rapporteringsperiode)

        val bekreftelse = slot.captured
        bekreftelse.periodeId shouldBe rapporteringsperiode.id.toString()
        bekreftelse.bekreftelsesLøsning shouldBe BekreftelsesLøsning.DAGPENGER
        val svar = bekreftelse.svar
        svar.gjelderFra shouldBe rapporteringsperiode.periode.fraOgMed.tilMillis()
        svar.gjelderTil shouldBe rapporteringsperiode.periode.tilOgMed.tilMillis()
        svar.harJobbetIDennePerioden shouldBe false
        svar.vilFortsetteSomArbeidssoeker shouldBe true
    }

    @Test
    fun `Kan sende bekreftelse med vilFortsetteSomArbeidssoeker = false`() {
        val ident = "01020312345"
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = false)

        val kallLoggService = mockk<KallLoggService>()
        every { kallLoggService.lagreKafkaUtKallLogg(any()) } returns 1
        every { kallLoggService.lagreRequest(eq(1), any()) } just runs
        every { kallLoggService.lagreResponse(eq(1), eq(200), eq("")) } just runs

        val recordKeyTokenProvider = {
            "TOKEN"
        }

        val bekreftelseKafkaProdusent = mockk<KafkaProdusent<ArbeidssøkerBekreftelse>>()
        val slot = slot<ArbeidssøkerBekreftelse>()
        every { bekreftelseKafkaProdusent.send(any(), capture(slot)) } just runs

        val arbeidssøkerService =
            ArbeidssøkerService(
                kallLoggService,
                recordKeyTokenProvider = recordKeyTokenProvider,
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
                httpClient = createMockClient(200, "{ \"key\": 123456789 }"),
            )

        arbeidssøkerService.sendBekreftelse(ident, rapporteringsperiode)

        val bekreftelse = slot.captured
        bekreftelse.periodeId shouldBe rapporteringsperiode.id.toString()
        bekreftelse.bekreftelsesLøsning shouldBe BekreftelsesLøsning.DAGPENGER
        val svar = bekreftelse.svar
        svar.gjelderFra shouldBe rapporteringsperiode.periode.fraOgMed.tilMillis()
        svar.gjelderTil shouldBe rapporteringsperiode.periode.tilOgMed.tilMillis()
        svar.harJobbetIDennePerioden shouldBe false
        svar.vilFortsetteSomArbeidssoeker shouldBe false
    }

    @Test
    fun `Kaster Exception hvis ikke kan hente recordKey token`() {
        val ident = "01020312345"
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = false)

        val kallLoggService = mockk<KallLoggService>()

        val recordKeyTokenProvider = {
            null
        }

        val bekreftelseKafkaProdusent = mockk<KafkaProdusent<ArbeidssøkerBekreftelse>>()

        val arbeidssøkerService =
            ArbeidssøkerService(
                kallLoggService,
                recordKeyTokenProvider = recordKeyTokenProvider,
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
                httpClient = createMockClient(200, "{ \"key\": 123456789 }"),
            )

        val exception =
            shouldThrow<RuntimeException> {
                arbeidssøkerService.sendBekreftelse(ident, rapporteringsperiode)
            }

        exception.message shouldBe "Klarte ikke å hente token"
    }

    @Test
    fun `Kaster Exception hvis ikke kan hente recordKey`() {
        val ident = "01020312345"
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = false)

        val kallLoggService = mockk<KallLoggService>()

        val recordKeyTokenProvider = {
            "TOKEN"
        }

        val bekreftelseKafkaProdusent = mockk<KafkaProdusent<ArbeidssøkerBekreftelse>>()

        val arbeidssøkerService =
            ArbeidssøkerService(
                kallLoggService,
                recordKeyTokenProvider = recordKeyTokenProvider,
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
                httpClient = createMockClient(500, ""),
            )

        val exception =
            shouldThrow<RuntimeException> {
                arbeidssøkerService.sendBekreftelse(ident, rapporteringsperiode)
            }

        exception.message shouldBe "Uforventet status 500 ved henting av record key"
    }

    @Test
    fun `Kaster Exception hvis ikke kan sende til Kafka`() {
        val ident = "01020312345"
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = true)

        val kallLoggService = mockk<KallLoggService>()
        every { kallLoggService.lagreKafkaUtKallLogg(any()) } returns 1
        every { kallLoggService.lagreRequest(eq(1), any()) } just runs
        every { kallLoggService.lagreResponse(eq(1), eq(500), eq("")) } just runs

        val recordKeyTokenProvider = {
            "TOKEN"
        }

        val bekreftelseKafkaProdusent = mockk<KafkaProdusent<ArbeidssøkerBekreftelse>>()
        every { bekreftelseKafkaProdusent.send(any(), any()) } throws RuntimeException("Kunne ikke sende til Kafka")

        val arbeidssøkerService =
            ArbeidssøkerService(
                kallLoggService,
                recordKeyTokenProvider = recordKeyTokenProvider,
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
                httpClient = createMockClient(200, "{ \"key\": 123456789 }"),
            )

        val exception =
            shouldThrow<Exception> {
                arbeidssøkerService.sendBekreftelse(ident, rapporteringsperiode)
            }

        exception.message shouldBe "java.lang.RuntimeException: Kunne ikke sende til Kafka"
    }
}
