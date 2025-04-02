package no.nav.dagpenger.rapportering.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.config.Configuration
import no.nav.dagpenger.rapportering.config.Configuration.ZONE_ID
import no.nav.dagpenger.rapportering.config.Configuration.bekreftelseTopic
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.config.Configuration.unleash
import no.nav.dagpenger.rapportering.kafka.sendDeferred
import no.nav.dagpenger.rapportering.model.ArbeidssøkerperiodeRequestBody
import no.nav.dagpenger.rapportering.model.ArbeidssøkerperiodeResponse
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RecordKeyRequestBody
import no.nav.dagpenger.rapportering.model.RecordKeyResponse
import no.nav.dagpenger.rapportering.model.arbeidet
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import no.nav.paw.bekreftelse.melding.v1.vo.Bekreftelsesloesning
import no.nav.paw.bekreftelse.melding.v1.vo.Bruker
import no.nav.paw.bekreftelse.melding.v1.vo.BrukerType
import no.nav.paw.bekreftelse.melding.v1.vo.Metadata
import no.nav.paw.bekreftelse.melding.v1.vo.Svar
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.net.URI
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

class ArbeidssøkerService(
    private val kallLoggService: KallLoggService,
    private val personregisterService: PersonregisterService,
    private val httpClient: HttpClient,
    private val bekreftelseKafkaProdusent: Producer<Long, Bekreftelse>,
    private val recordKeyUrl: String = Configuration.arbeidssokerregisterRecordKeyUrl,
    private val recordKeyTokenProvider: () -> String? = Configuration.arbeidssokerregisterRecordKeyTokenProvider,
    private val oppslagUrl: String = Configuration.arbeidssokerregisterOppslagUrl,
    private val oppslagTokenProvider: () -> String? = Configuration.arbeidssokerregisterOppslagTokenProvider,
) {
    private val logger = KotlinLogging.logger {}
    private val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")

    private val cache: LoadingCache<String, List<ArbeidssøkerperiodeResponse>> = Caffeine.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build { ident: String -> runBlocking { hentArbeidssøkerperioder(ident) } }

    fun hentCachedArbeidssøkerperioder(ident: String): List<ArbeidssøkerperiodeResponse> {
        return cache.get(ident)
    }

    fun sendBekreftelse(
        ident: String,
        token: String,
        loginLevel: Int,
        rapporteringsperiode: Rapporteringsperiode,
    ) {
        if (!unleash.isEnabled("send-arbeidssoekerstatus")) {
            return
        }

        if (!personregisterService.erBekreftelseOvertatt(ident, token)) {
            return
        }

        val recordKeyResponse = runBlocking { hentRecordKey(ident) }
        val arbeidssøkerperiodeResponse = cache.get(ident).firstOrNull()

        if (arbeidssøkerperiodeResponse == null) {
            logger.info { "Kunne ikke hente arbeidssøkerperiode. Sender ikke sp.5 til PAW" }
            return
        }

        val arbeidssøkerBekreftelse =
            Bekreftelse(
                arbeidssøkerperiodeResponse.periodeId,
                Bekreftelsesloesning.DAGPENGER,
                UUID.randomUUID(),
                Svar(
                    Metadata(
                        Instant.now().atZone(ZONE_ID).toInstant(),
                        Bruker(BrukerType.SLUTTBRUKER, ident, "tokenx:Level$loginLevel"),
                        Bekreftelsesloesning.DAGPENGER.name,
                        "Bruker sendte inn dagpengermeldekort",
                    ),
                    rapporteringsperiode.periode.fraOgMed
                        .atStartOfDay()
                        .atZone(ZONE_ID)
                        .toInstant(),
                    rapporteringsperiode.periode.tilOgMed
                        .plusDays(1)
                        .atStartOfDay()
                        .atZone(ZONE_ID)
                        .toInstant(),
                    rapporteringsperiode.arbeidet(),
                    rapporteringsperiode.registrertArbeidssoker == true,
                ),
            )

        val kallLoggId = kallLoggService.lagreKafkaUtKallLogg(ident)
        kallLoggService.lagreRequest(kallLoggId, arbeidssøkerBekreftelse.toString())

        try {
            val record = ProducerRecord(bekreftelseTopic, recordKeyResponse.key, arbeidssøkerBekreftelse)
            val metadata = runBlocking { bekreftelseKafkaProdusent.sendDeferred(record).await() }
            sikkerlogg.info {
                "Sendt arbeidssøkerstatus for ident = $ident til Team PAW. " +
                    "Metadata: topic=${metadata.topic()} (partition=${metadata.partition()}, offset=${metadata.offset()})"
            }

            kallLoggService.lagreResponse(kallLoggId, 200, "")
        } catch (e: Exception) {
            logger.error("Kunne ikke sende arbeidssøkerstatus til Kafka", e)

            kallLoggService.lagreResponse(kallLoggId, 500, "")

            throw Exception(e)
        }
    }

    private suspend fun hentRecordKey(ident: String): RecordKeyResponse =
        withContext(Dispatchers.IO) {
            try {
                val result =
                    httpClient
                        .post(URI(recordKeyUrl).toURL()) {
                            bearerAuth(
                                recordKeyTokenProvider.invoke() ?: throw RuntimeException("Klarte ikke å hente token"),
                            )
                            contentType(ContentType.Application.Json)
                            setBody(defaultObjectMapper.writeValueAsString(RecordKeyRequestBody(ident)))
                        }.also {
                            sikkerlogg.info {
                                "Kall til arbeidssøkerregister for å hente record key for $ident ga status ${it.status}"
                            }
                        }

                if (result.status != HttpStatusCode.OK) {
                    val body = result.bodyAsText()
                    sikkerlogg.warn {
                        "Uforventet status ${result.status.value} ved henting av record key for $ident. Response: $body"
                    }
                    throw RuntimeException("Uforventet status ${result.status.value} ved henting av record key")
                }
                result.body()
            } catch (e: Exception) {
                logger.error(e) { "Kunne ikke hente record key" }
                throw RuntimeException(e)
            }
        }

    private suspend fun hentArbeidssøkerperioder(ident: String): List<ArbeidssøkerperiodeResponse> =
        withContext(Dispatchers.IO) {
            try {
                val result =
                    httpClient
                        .post(URI(oppslagUrl).toURL()) {
                            bearerAuth(
                                oppslagTokenProvider.invoke() ?: throw RuntimeException("Klarte ikke å hente token"),
                            )
                            contentType(ContentType.Application.Json)
                            parameter("siste", true)
                            setBody(defaultObjectMapper.writeValueAsString(ArbeidssøkerperiodeRequestBody(ident)))
                        }.also {
                            sikkerlogg.info {
                                "Kall til arbeidssøkerregister for å hente arbeidssøkerperiode for $ident ga status ${it.status}"
                            }
                        }

                if (result.status != HttpStatusCode.OK) {
                    val body = result.bodyAsText()
                    sikkerlogg.warn {
                        "Uforventet status ${result.status.value} ved henting av arbeidssøkerperiode for $ident. Response: $body"
                    }
                    throw RuntimeException("Uforventet status ${result.status.value} ved henting av arbeidssøkerperiode")
                }

                val response: List<ArbeidssøkerperiodeResponse> = result.body()

                response
            } catch (e: Exception) {
                logger.error(e) { "Kunne ikke hente arbeidssøkerperioder" }
                throw RuntimeException(e)
            }
        }
}
