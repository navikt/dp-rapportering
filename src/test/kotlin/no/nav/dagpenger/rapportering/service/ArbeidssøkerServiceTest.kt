package no.nav.dagpenger.rapportering.service

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import no.nav.dagpenger.rapportering.api.ApiTestSetup.Companion.setEnvConfig
import no.nav.dagpenger.rapportering.api.rapporteringsperiodeFor
import no.nav.dagpenger.rapportering.config.Configuration.ZONE_ID
import no.nav.dagpenger.rapportering.config.Configuration.unleash
import no.nav.dagpenger.rapportering.connector.createHttpClient
import no.nav.dagpenger.rapportering.connector.createMockClient
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import no.nav.paw.bekreftelse.melding.v1.vo.Bekreftelsesloesning
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

class ArbeidssøkerServiceTest {
    private val ident = "01020312345"
    private val arbeidsregisterPeriodeId = "68219fd0-98d1-4ae9-8ddd-19bca28de5ee"
    private val recordKeyTokenProvider = {
        "TOKEN"
    }
    private val oppslagTokenProvider = {
        "TOKEN"
    }
    private val topicPartition = TopicPartition("test-topic", 1)
    private val recordMetadata = RecordMetadata(topicPartition, 1L, 2, 3L, 4, 5)

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            setEnvConfig()

            mockkObject(unleash)
        }
    }

    @Test
    fun `Kan sende bekreftelse med vilFortsetteSomArbeidssoeker = true`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = true)

        val kallLoggService = mockk<KallLoggService>()
        every { kallLoggService.lagreKafkaUtKallLogg(any()) } returns 1
        every { kallLoggService.lagreRequest(eq(1), any()) } just runs
        every { kallLoggService.lagreResponse(eq(1), eq(200), eq("")) } just runs

        val personregisterService = mockk<PersonregisterService>()
        every { personregisterService.erBekreftelseOvertatt(eq(ident), any()) } returns true

        val bekreftelseKafkaProdusent = mockk<Producer<Long, Bekreftelse>>()
        val slot = slot<ProducerRecord<Long, Bekreftelse>>()
        every { bekreftelseKafkaProdusent.send(capture(slot), any()) } answers {
            secondArg<Callback>().onCompletion(recordMetadata, null)
            CompletableFuture.completedFuture(recordMetadata)
        }

        every { unleash.isEnabled(eq("send-arbeidssoekerstatus")) } returns true

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                personregisterService = personregisterService,
                httpClient = mockHttpClient(),
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
                recordKeyTokenProvider = recordKeyTokenProvider,
                oppslagTokenProvider = oppslagTokenProvider,
            )

        arbeidssoekerService.sendBekreftelse(ident, "", rapporteringsperiode)

        val bekreftelse = slot.captured.value()
        bekreftelse.periodeId.toString() shouldBe arbeidsregisterPeriodeId
        bekreftelse.bekreftelsesloesning shouldBe Bekreftelsesloesning.DAGPENGER
        val svar = bekreftelse.svar
        svar.gjelderFra shouldBe rapporteringsperiode.periode.fraOgMed.atStartOfDay().atZone(ZONE_ID).toInstant()
        svar.gjelderTil shouldBe rapporteringsperiode.periode.tilOgMed.atStartOfDay().atZone(ZONE_ID).toInstant()
        svar.harJobbetIDennePerioden shouldBe false
        svar.vilFortsetteSomArbeidssoeker shouldBe true
    }

    @Test
    fun `Skal ikke sende bekreftelse hvis Unleash send-arbeidssoekerstatus returnerer false`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = true)

        val kallLoggService = mockk<KallLoggService>()

        val personregisterService = mockk<PersonregisterService>()
        every { personregisterService.erBekreftelseOvertatt(eq(ident), any()) } returns true

        val bekreftelseKafkaProdusent = mockk<Producer<Long, Bekreftelse>>()
        // send() skal ikke kalles og vi kaster Exception hvis det skjer
        every { bekreftelseKafkaProdusent.send(any(), any()) } throws Exception()

        every { unleash.isEnabled(eq("send-arbeidssoekerstatus")) } returns false

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                personregisterService = personregisterService,
                httpClient = mockHttpClient(),
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
                recordKeyTokenProvider = recordKeyTokenProvider,
                oppslagTokenProvider = oppslagTokenProvider,
            )

        arbeidssoekerService.sendBekreftelse(ident, "", rapporteringsperiode)
    }

    @Test
    fun `Skal ikke sende bekreftelse hvis erBekreftelseOvertatt returnerer false`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = true)

        val kallLoggService = mockk<KallLoggService>()

        val personregisterService = mockk<PersonregisterService>()
        every { personregisterService.erBekreftelseOvertatt(eq(ident), any()) } returns false

        val bekreftelseKafkaProdusent = mockk<Producer<Long, Bekreftelse>>()
        // send() skal ikke kalles og vi kaster Exception hvis det skjer
        every { bekreftelseKafkaProdusent.send(any(), any()) } throws Exception()

        every { unleash.isEnabled(eq("send-arbeidssoekerstatus")) } returns true

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                personregisterService = personregisterService,
                httpClient = mockHttpClient(),
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
                recordKeyTokenProvider = recordKeyTokenProvider,
                oppslagTokenProvider = oppslagTokenProvider,
            )

        arbeidssoekerService.sendBekreftelse(ident, "", rapporteringsperiode)
    }

    @Test
    fun `Kan sende bekreftelse med vilFortsetteSomArbeidssoeker = false`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = false)

        val kallLoggService = mockk<KallLoggService>()
        every { kallLoggService.lagreKafkaUtKallLogg(any()) } returns 1
        every { kallLoggService.lagreRequest(eq(1), any()) } just runs
        every { kallLoggService.lagreResponse(eq(1), eq(200), eq("")) } just runs

        val personregisterService = mockk<PersonregisterService>()
        every { personregisterService.erBekreftelseOvertatt(eq(ident), any()) } returns true

        val bekreftelseKafkaProdusent = mockk<Producer<Long, Bekreftelse>>()
        val slot = slot<ProducerRecord<Long, Bekreftelse>>()
        every { bekreftelseKafkaProdusent.send(capture(slot), any()) } answers {
            secondArg<Callback>().onCompletion(recordMetadata, null)
            CompletableFuture.completedFuture(recordMetadata)
        }

        every { unleash.isEnabled(eq("send-arbeidssoekerstatus")) } returns true

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                personregisterService = personregisterService,
                httpClient = mockHttpClient(),
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
                recordKeyTokenProvider = recordKeyTokenProvider,
                oppslagTokenProvider = oppslagTokenProvider,
            )

        arbeidssoekerService.sendBekreftelse(ident, "", rapporteringsperiode)

        val bekreftelse = slot.captured.value()
        bekreftelse.periodeId.toString() shouldBe arbeidsregisterPeriodeId
        bekreftelse.bekreftelsesloesning shouldBe Bekreftelsesloesning.DAGPENGER
        val svar = bekreftelse.svar
        svar.gjelderFra shouldBe rapporteringsperiode.periode.fraOgMed.atStartOfDay().atZone(ZONE_ID).toInstant()
        svar.gjelderTil shouldBe rapporteringsperiode.periode.tilOgMed.atStartOfDay().atZone(ZONE_ID).toInstant()
        svar.harJobbetIDennePerioden shouldBe false
        svar.vilFortsetteSomArbeidssoeker shouldBe false
    }

    @Test
    fun `Kaster Exception hvis ikke kan hente recordKey token`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = false)

        val kallLoggService = mockk<KallLoggService>()
        val personregisterService = mockk<PersonregisterService>()
        every { personregisterService.erBekreftelseOvertatt(eq(ident), any()) } returns true

        val recordKeyTokenProvider = {
            null
        }

        val bekreftelseKafkaProdusent = mockk<Producer<Long, Bekreftelse>>()

        every { unleash.isEnabled(eq("send-arbeidssoekerstatus")) } returns true

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                personregisterService = personregisterService,
                httpClient = mockHttpClient(),
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
                recordKeyTokenProvider = recordKeyTokenProvider,
                oppslagTokenProvider = oppslagTokenProvider,
            )

        val exception =
            shouldThrow<RuntimeException> {
                arbeidssoekerService.sendBekreftelse(ident, "", rapporteringsperiode)
            }

        exception.message shouldBe "java.lang.RuntimeException: Klarte ikke å hente token"
    }

    @Test
    fun `Kaster Exception hvis ikke kan hente recordKey`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = false)

        val kallLoggService = mockk<KallLoggService>()

        val personregisterService = mockk<PersonregisterService>()
        every { personregisterService.erBekreftelseOvertatt(eq(ident), any()) } returns true

        val bekreftelseKafkaProdusent = mockk<Producer<Long, Bekreftelse>>()

        every { unleash.isEnabled(eq("send-arbeidssoekerstatus")) } returns true

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                personregisterService = personregisterService,
                httpClient = createMockClient(500, ""),
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
                recordKeyTokenProvider = recordKeyTokenProvider,
            )

        val exception =
            shouldThrow<RuntimeException> {
                arbeidssoekerService.sendBekreftelse(ident, "", rapporteringsperiode)
            }

        exception.message shouldBe "java.lang.RuntimeException: Uforventet status 500 ved henting av record key"
    }

    @Test
    fun `Kaster Exception hvis ikke kan hente oppslag token`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = false)

        val kallLoggService = mockk<KallLoggService>()

        val personregisterService = mockk<PersonregisterService>()
        every { personregisterService.erBekreftelseOvertatt(eq(ident), any()) } returns true

        val bekreftelseKafkaProdusent = mockk<Producer<Long, Bekreftelse>>()

        val oppslagTokenProvider = {
            null
        }

        every { unleash.isEnabled(eq("send-arbeidssoekerstatus")) } returns true

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                personregisterService = personregisterService,
                httpClient = mockHttpClient(),
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
                recordKeyTokenProvider = recordKeyTokenProvider,
                oppslagTokenProvider = oppslagTokenProvider,
            )

        val exception =
            shouldThrow<RuntimeException> {
                arbeidssoekerService.sendBekreftelse(ident, "", rapporteringsperiode)
            }

        exception.message shouldBe "java.lang.RuntimeException: Klarte ikke å hente token"
    }

    @Test
    fun `Kaster Exception hvis ikke kan hente arbeidssøkerperioder`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = false)

        val kallLoggService = mockk<KallLoggService>()

        val personregisterService = mockk<PersonregisterService>()
        every { personregisterService.erBekreftelseOvertatt(eq(ident), any()) } returns true

        val bekreftelseKafkaProdusent = mockk<Producer<Long, Bekreftelse>>()

        every { unleash.isEnabled(eq("send-arbeidssoekerstatus")) } returns true

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                personregisterService = personregisterService,
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
                recordKeyTokenProvider = recordKeyTokenProvider,
                oppslagTokenProvider = oppslagTokenProvider,
                // Returnerer svar kun for RecordKey slik at hentSisteArbeidssøkerperiode kaster exception
                httpClient =
                    createHttpClient(
                        MockEngine {
                            respond(
                                content = "{ \"key\": 123456789 }",
                                status = HttpStatusCode.fromValue(200),
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        },
                    ),
            )

        val exception =
            shouldThrow<RuntimeException> {
                arbeidssoekerService.sendBekreftelse(ident, "", rapporteringsperiode)
            }

        exception.message shouldStartWith "io.ktor.serialization.JsonConvertException: Illegal json parameter found"
    }

    @Test
    fun `Kaster ikke Exception hvis henter en tom liste med arbeidssøkerperioder`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = false)

        val kallLoggService = mockk<KallLoggService>()

        val personregisterService = mockk<PersonregisterService>()
        every { personregisterService.erBekreftelseOvertatt(eq(ident), any()) } returns true

        val bekreftelseKafkaProdusent = mockk<Producer<Long, Bekreftelse>>()
        // send() skal ikke kalles og vi kaster Exception hvis det skjer
        every { bekreftelseKafkaProdusent.send(any(), any()) } throws Exception()
        every { unleash.isEnabled(eq("send-arbeidssoekerstatus")) } returns true

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                personregisterService = personregisterService,
                httpClient = mockHttpClient(true),
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
                recordKeyTokenProvider = recordKeyTokenProvider,
                oppslagTokenProvider = oppslagTokenProvider,
            )

        shouldNotThrow<RuntimeException> {
            arbeidssoekerService.sendBekreftelse(ident, "", rapporteringsperiode)
        }
    }

    @Test
    fun `Kaster Exception hvis ikke kan sende til Kafka`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = true)

        val kallLoggService = mockk<KallLoggService>()
        every { kallLoggService.lagreKafkaUtKallLogg(any()) } returns 1
        every { kallLoggService.lagreRequest(eq(1), any()) } just runs
        every { kallLoggService.lagreResponse(eq(1), eq(500), eq("")) } just runs

        val personregisterService = mockk<PersonregisterService>()
        every { personregisterService.erBekreftelseOvertatt(eq(ident), any()) } returns true

        val bekreftelseKafkaProdusent = mockk<Producer<Long, Bekreftelse>>()
        every { bekreftelseKafkaProdusent.send(any(), any()) } throws RuntimeException("Kunne ikke sende til Kafka")

        every { unleash.isEnabled(eq("send-arbeidssoekerstatus")) } returns true

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                personregisterService = personregisterService,
                httpClient = mockHttpClient(),
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
                recordKeyTokenProvider = recordKeyTokenProvider,
                oppslagTokenProvider = oppslagTokenProvider,
            )

        val exception =
            shouldThrow<Exception> {
                arbeidssoekerService.sendBekreftelse(ident, "", rapporteringsperiode)
            }

        exception.message shouldBe "java.lang.RuntimeException: Kunne ikke sende til Kafka"
    }

    private fun mockHttpClient(tomPeriodeListe: Boolean = false): HttpClient {
        return createHttpClient(
            MockEngine { request ->
                respond(
                    content =
                        if (request.url.host == "arbeidssokerregister_record_key_url") {
                            "{ \"key\": 123456789 }"
                        } else {
                            if (tomPeriodeListe) {
                                "[]"
                            } else {
                                """
                                [
                                  {
                                    "periodeId": "$arbeidsregisterPeriodeId",
                                    "startet": {
                                      "tidspunkt": "2025-02-04T10:15:30",
                                      "utfoertAv": {
                                        "type": "Type",
                                        "id": "1"
                                      },
                                      "kilde": "Kilde",
                                      "aarsak": "Årsak"
                                    }
                                  }
                                ]
                                """.trimIndent()
                            }
                        },
                    status = HttpStatusCode.fromValue(200),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )
    }
}
