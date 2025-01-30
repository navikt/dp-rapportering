package no.nav.dagpenger.rapportering.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
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
import no.nav.dagpenger.rapportering.model.ArbeidssøkerBekreftelse
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.arbeidet
import no.nav.dagpenger.rapportering.utils.tilMillis
import java.lang.System.getenv
import java.net.URI
import java.util.UUID

class ArbeidssøkerService(
    private val kallLoggService: KallLoggService,
    private val recordKeyUrl: String = Configuration.arbeidssokerregisterRecordKeyUrl,
    private val recordKeyTokenProvider: () -> String? = Configuration.arbeidssokerregisterRecordKeyTokenProvider,
    private val bekreftelseKafkaProdusent: KafkaProdusent<ArbeidssøkerBekreftelse> = Configuration.bekreftelseKafkaProdusent,
    private val httpClient: HttpClient = defaultHttpClient(),
) {
    private val logger = KotlinLogging.logger {}

    suspend fun hentRecordKey(ident: String): RecordKeyResponse =
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

    fun sendBekreftelse(
        ident: String,
        rapporteringsperiode: Rapporteringsperiode,
    ) {
        // Sender ikke i prod ennå
        if (getenv("NAIS_CLUSTER_NAME") == "prod-gcp") {
            return
        }

        val recordKeyResponse = runBlocking { hentRecordKey(ident) }

        val arbeidssøkerBekreftelse =
            ArbeidssøkerBekreftelse(
                periodeId = rapporteringsperiode.id.toString(),
                // TODO: partisjonsnøkkel?
                id = UUID.randomUUID(),
                svar =
                    ArbeidssøkerBekreftelse.Svar(
                        gjelderFra = rapporteringsperiode.periode.fraOgMed.tilMillis(),
                        gjelderTil = rapporteringsperiode.periode.tilOgMed.tilMillis(),
                        harJobbetIDennePerioden = rapporteringsperiode.arbeidet(),
                        vilFortsetteSomArbeidssoeker = rapporteringsperiode.registrertArbeidssoker == true,
                    ),
            )

        val kallLoggId = kallLoggService.lagreKafkaUtKallLogg(ident)
        kallLoggService.lagreRequest(kallLoggId, defaultObjectMapper.writeValueAsString(arbeidssøkerBekreftelse))

        try {
            bekreftelseKafkaProdusent.send(key = recordKeyResponse.key, value = arbeidssøkerBekreftelse)

            kallLoggService.lagreResponse(kallLoggId, 200, "")
        } catch (e: Exception) {
            logger.error("Kunne ikke sende arbeidssøkerstatus til Kafka", e)

            kallLoggService.lagreResponse(kallLoggId, 500, "")

            throw Exception(e)
        }
    }

    companion object {
        private val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")
    }
}

data class RecordKeyRequestBody(
    val ident: String,
)

data class RecordKeyResponse(
    val key: Long,
)
