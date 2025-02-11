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
import io.mockk.runs
import io.mockk.slot
import no.nav.dagpenger.rapportering.api.rapporteringsperiodeFor
import no.nav.dagpenger.rapportering.connector.createHttpClient
import no.nav.dagpenger.rapportering.connector.createMockClient
import no.nav.dagpenger.rapportering.kafka.KafkaProdusent
import no.nav.dagpenger.rapportering.repository.Postgres.database
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import no.nav.paw.bekreftelse.melding.v1.vo.Bekreftelsesloesning
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

class ArbeidssøkerServiceTest {
    private val ident = "01020312345"
    private val arbeidsregisterPeriodeId = "68219fd0-98d1-4ae9-8ddd-19bca28de5ee"
    private val recordKeyTokenProvider = {
        "TOKEN"
    }
    private val oppslagTokenProvider = {
        "TOKEN"
    }

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
            System.setProperty(
                "ARBEIDSSOKERREGISTER_OPPSLAG_URL",
                "http://arbeidssokerregister_oppslag_url/api/v1/arbeidssoekerperioder",
            )
        }
    }

    @Test
    fun `Kan sende bekreftelse med vilFortsetteSomArbeidssoeker = true`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = true)

        val kallLoggService = mockk<KallLoggService>()
        every { kallLoggService.lagreKafkaUtKallLogg(any()) } returns 1
        every { kallLoggService.lagreRequest(eq(1), any()) } just runs
        every { kallLoggService.lagreResponse(eq(1), eq(200), eq("")) } just runs

        val bekreftelseKafkaProdusent = mockk<KafkaProdusent>()
        val slot = slot<Bekreftelse>()
        every { bekreftelseKafkaProdusent.send(any(), capture(slot)) } just runs

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                httpClient = mockHttpClient(),
                recordKeyTokenProvider = recordKeyTokenProvider,
                oppslagTokenProvider = oppslagTokenProvider,
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
            )

        arbeidssoekerService.sendBekreftelse(ident, rapporteringsperiode)

        val bekreftelse = slot.captured
        bekreftelse.periodeId.toString() shouldBe arbeidsregisterPeriodeId
        bekreftelse.bekreftelsesloesning shouldBe Bekreftelsesloesning.DAGPENGER
        val svar = bekreftelse.svar
        svar.gjelderFra shouldBe rapporteringsperiode.periode.fraOgMed.atStartOfDay().toInstant(ZoneOffset.UTC)
        svar.gjelderTil shouldBe rapporteringsperiode.periode.tilOgMed.atStartOfDay().toInstant(ZoneOffset.UTC)
        svar.harJobbetIDennePerioden shouldBe false
        svar.vilFortsetteSomArbeidssoeker shouldBe true
    }

    @Test
    fun `Kan sende bekreftelse med vilFortsetteSomArbeidssoeker = false`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = false)

        val kallLoggService = mockk<KallLoggService>()
        every { kallLoggService.lagreKafkaUtKallLogg(any()) } returns 1
        every { kallLoggService.lagreRequest(eq(1), any()) } just runs
        every { kallLoggService.lagreResponse(eq(1), eq(200), eq("")) } just runs

        val bekreftelseKafkaProdusent = mockk<KafkaProdusent>()
        val slot = slot<Bekreftelse>()
        every { bekreftelseKafkaProdusent.send(any(), capture(slot)) } just runs

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                httpClient = mockHttpClient(),
                recordKeyTokenProvider = recordKeyTokenProvider,
                oppslagTokenProvider = oppslagTokenProvider,
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
            )

        arbeidssoekerService.sendBekreftelse(ident, rapporteringsperiode)

        val bekreftelse = slot.captured
        bekreftelse.periodeId.toString() shouldBe arbeidsregisterPeriodeId
        bekreftelse.bekreftelsesloesning shouldBe Bekreftelsesloesning.DAGPENGER
        val svar = bekreftelse.svar
        svar.gjelderFra shouldBe rapporteringsperiode.periode.fraOgMed.atStartOfDay().toInstant(ZoneOffset.UTC)
        svar.gjelderTil shouldBe rapporteringsperiode.periode.tilOgMed.atStartOfDay().toInstant(ZoneOffset.UTC)
        svar.harJobbetIDennePerioden shouldBe false
        svar.vilFortsetteSomArbeidssoeker shouldBe false
    }

    @Test
    fun `Kaster Exception hvis ikke kan hente recordKey token`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = false)

        val kallLoggService = mockk<KallLoggService>()

        val recordKeyTokenProvider = {
            null
        }

        val bekreftelseKafkaProdusent = mockk<KafkaProdusent>()

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                httpClient = mockHttpClient(),
                recordKeyTokenProvider = recordKeyTokenProvider,
                oppslagTokenProvider = oppslagTokenProvider,
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
            )

        val exception =
            shouldThrow<RuntimeException> {
                arbeidssoekerService.sendBekreftelse(ident, rapporteringsperiode)
            }

        exception.message shouldBe "java.lang.RuntimeException: Klarte ikke å hente token"
    }

    @Test
    fun `Kaster Exception hvis ikke kan hente recordKey`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = false)

        val kallLoggService = mockk<KallLoggService>()
        val bekreftelseKafkaProdusent = mockk<KafkaProdusent>()

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                httpClient = createMockClient(500, ""),
                recordKeyTokenProvider = recordKeyTokenProvider,
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
            )

        val exception =
            shouldThrow<RuntimeException> {
                arbeidssoekerService.sendBekreftelse(ident, rapporteringsperiode)
            }

        exception.message shouldBe "java.lang.RuntimeException: Uforventet status 500 ved henting av record key"
    }

    @Test
    fun `Kaster Exception hvis ikke kan hente oppslag token`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = false)

        val kallLoggService = mockk<KallLoggService>()

        val oppslagTokenProvider = {
            null
        }

        val bekreftelseKafkaProdusent = mockk<KafkaProdusent>()

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                httpClient = mockHttpClient(),
                recordKeyTokenProvider = recordKeyTokenProvider,
                oppslagTokenProvider = oppslagTokenProvider,
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
            )

        val exception =
            shouldThrow<RuntimeException> {
                arbeidssoekerService.sendBekreftelse(ident, rapporteringsperiode)
            }

        exception.message shouldBe "java.lang.RuntimeException: Klarte ikke å hente token"
    }

    @Test
    fun `Kaster Exception hvis ikke kan hente arbeidssøkerperioder`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = false)

        val kallLoggService = mockk<KallLoggService>()
        val bekreftelseKafkaProdusent = mockk<KafkaProdusent>()

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                recordKeyTokenProvider = recordKeyTokenProvider,
                oppslagTokenProvider = oppslagTokenProvider,
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
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
                arbeidssoekerService.sendBekreftelse(ident, rapporteringsperiode)
            }

        exception.message shouldStartWith "io.ktor.serialization.JsonConvertException: Illegal json parameter found"
    }

    @Test
    fun `Kaster ikke Exception hvis henter en tom liste med arbeidssøkerperioder`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = false)

        val kallLoggService = mockk<KallLoggService>()
        val bekreftelseKafkaProdusent = mockk<KafkaProdusent>()

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                httpClient = mockHttpClient(true),
                recordKeyTokenProvider = recordKeyTokenProvider,
                oppslagTokenProvider = oppslagTokenProvider,
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
            )

        shouldNotThrow<RuntimeException> {
            arbeidssoekerService.sendBekreftelse(ident, rapporteringsperiode)
        }
    }

    @Test
    fun `Kaster Exception hvis ikke kan sende til Kafka`() {
        val rapporteringsperiode = rapporteringsperiodeFor(registrertArbeidssoker = true)

        val kallLoggService = mockk<KallLoggService>()
        every { kallLoggService.lagreKafkaUtKallLogg(any()) } returns 1
        every { kallLoggService.lagreRequest(eq(1), any()) } just runs
        every { kallLoggService.lagreResponse(eq(1), eq(500), eq("")) } just runs

        val bekreftelseKafkaProdusent = mockk<KafkaProdusent>()
        every { bekreftelseKafkaProdusent.send(any(), any()) } throws RuntimeException("Kunne ikke sende til Kafka")

        val arbeidssoekerService =
            ArbeidssøkerService(
                kallLoggService = kallLoggService,
                httpClient = mockHttpClient(),
                recordKeyTokenProvider = recordKeyTokenProvider,
                oppslagTokenProvider = oppslagTokenProvider,
                bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
            )

        val exception =
            shouldThrow<Exception> {
                arbeidssoekerService.sendBekreftelse(ident, rapporteringsperiode)
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
