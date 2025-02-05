package no.nav.dagpenger.rapportering.service

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
import no.nav.dagpenger.oauth2.defaultHttpClient
import no.nav.dagpenger.rapportering.config.Configuration
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.kafka.KafkaProdusent
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
import java.lang.System.getenv
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class ArbeidssøkerService(
    private val kallLoggService: KallLoggService,
    private val recordKeyUrl: String = Configuration.arbeidssokerregisterRecordKeyUrl,
    private val recordKeyTokenProvider: () -> String? = Configuration.arbeidssokerregisterRecordKeyTokenProvider,
    private val oppslagUrl: String = Configuration.arbeidssokerregisterOppslagUrl,
    private val oppslagTokenProvider: () -> String? = Configuration.arbeidssokerregisterOppslagTokenProvider,
    private val bekreftelseKafkaProdusent: KafkaProdusent = Configuration.bekreftelseKafkaProdusent,
    private val httpClient: HttpClient = defaultHttpClient(),
) {
    private val logger = KotlinLogging.logger {}
    private val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")

    fun sendBekreftelse(
        ident: String,
        rapporteringsperiode: Rapporteringsperiode,
    ) {
        // Sender ikke i prod ennå
        if (getenv("NAIS_CLUSTER_NAME") == "prod-gcp") {
            return
        }

        val recordKeyResponse = runBlocking { hentRecordKey(ident) }
        val arbeidssøkerperiodeResponse = runBlocking { hentSisteArbeidssøkerperiode(ident) }

        val arbeidssøkerBekreftelse =
            Bekreftelse(
                arbeidssøkerperiodeResponse.periodeId,
                Bekreftelsesloesning.DAGPENGER,
                UUID.randomUUID(),
                Svar(
                    Metadata(
                        LocalDateTime.now().toInstant(ZoneOffset.UTC),
                        Bruker(BrukerType.SLUTTBRUKER, ident),
                        Bekreftelsesloesning.DAGPENGER.name,
                        "Bruker sendte inn dagpengermeldekort",
                    ),
                    rapporteringsperiode.periode.fraOgMed.atStartOfDay().toInstant(ZoneOffset.UTC),
                    rapporteringsperiode.periode.tilOgMed.atStartOfDay().toInstant(ZoneOffset.UTC),
                    rapporteringsperiode.arbeidet(),
                    rapporteringsperiode.registrertArbeidssoker == true,
                ),
            )

        val kallLoggId = kallLoggService.lagreKafkaUtKallLogg(ident)
        kallLoggService.lagreRequest(kallLoggId, arbeidssøkerBekreftelse.toString())

        try {
            bekreftelseKafkaProdusent.send(key = recordKeyResponse.key, value = arbeidssøkerBekreftelse)

            kallLoggService.lagreResponse(kallLoggId, 200, "")
        } catch (e: Exception) {
            logger.error("Kunne ikke sende arbeidssøkerstatus til Kafka", e)

            kallLoggService.lagreResponse(kallLoggId, 500, "")

            throw Exception(e)
        }
    }

    private suspend fun hentRecordKey(ident: String): RecordKeyResponse =
        withContext(Dispatchers.IO) {
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
        }

    private suspend fun hentSisteArbeidssøkerperiode(ident: String): ArbeidssøkerperiodeResponse =
        withContext(Dispatchers.IO) {
            val result =
                httpClient
                    .post(URI(oppslagUrl).toURL()) {
                        bearerAuth(oppslagTokenProvider.invoke() ?: throw RuntimeException("Klarte ikke å hente token"))
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

            try {
                val response: List<ArbeidssøkerperiodeResponse> = result.body()

                response.first()
            } catch (e: Exception) {
                throw RuntimeException("Kunne ikke prosessere arbeidssøkerperioder")
            }
        }
}
