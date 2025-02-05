package no.nav.dagpenger.rapportering.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.PropertyGroup
import com.natpryce.konfig.getValue
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.oauth2.CachedOauth2Client
import no.nav.dagpenger.oauth2.OAuth2Config
import no.nav.dagpenger.rapportering.kafka.BekreftelseAvroSerializer
import no.nav.dagpenger.rapportering.kafka.KafkaProdusent
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.LongSerializer
import java.util.Properties

internal object Configuration {
    const val APP_NAME = "dp-rapportering"

    const val MDC_CORRELATION_ID = "correlationId"

    val NO_LOG_PATHS = setOf("/metrics", "/isAlive", "/isReady")

    private val defaultProperties =
        ConfigurationMap(
            mapOf(
                "beregningsdato_strategi" to "tom",
                "Grupper.saksbehandler" to "123",
                "RAPID_APP_NAME" to APP_NAME,
                "KAFKA_CONSUMER_GROUP_ID" to "dp-rapportering-v1",
                "KAFKA_RAPID_TOPIC" to "teamdagpenger.rapid.v1",
                "KAFKA_EXTRA_TOPIC" to "teamdagpenger.journalforing.v1",
                "KAFKA_RESET_POLICY" to "LATEST",
            ),
        )

    object Grupper : PropertyGroup() {
        val saksbehandler by stringType
    }

    val properties =
        ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding defaultProperties
    val config: Map<String, String> =
        properties.list().reversed().fold(emptyMap()) { map, pair ->
            map + pair.second
        }

    val meldepliktAdapterUrl by lazy {
        properties[Key("MELDEPLIKT_ADAPTER_HOST", stringType)].let {
            "https://$it"
        }
    }

    val meldepliktAdapterAudience by lazy { properties[Key("MELDEPLIKT_ADAPTER_AUDIENCE", stringType)] }

    val pdfGeneratorUrl by lazy { properties[Key("PDF_GENERATOR_URL", stringType)] }

    val arbeidssokerregisterRecordKeyUrl by lazy {
        properties[Key("ARBEIDSSOKERREGISTER_RECORD_KEY_URL", stringType)]
    }

    val arbeidssokerregisterRecordKeyTokenProvider: () -> String by lazy {
        {
            runBlocking {
                azureAdClient
                    .clientCredentials(properties[Key("ARBEIDSSOKERREGISTER_RECORD_KEY_SCOPE", stringType)])
                    .access_token ?: throw RuntimeException("Failed to get token")
            }
        }
    }

    val arbeidssokerregisterOppslagUrl by lazy {
        properties[Key("ARBEIDSSOKERREGISTER_OPPSLAG_KEY_URL", stringType)]
    }

    val arbeidssokerregisterOppslagTokenProvider: () -> String by lazy {
        {
            runBlocking {
                azureAdClient
                    .clientCredentials(properties[Key("ARBEIDSSOKERREGISTER_OPPSLAG_SCOPE", stringType)])
                    .access_token ?: throw RuntimeException("Failed to get token")
            }
        }
    }

    val bekreftelseTopic by lazy { properties[Key("BEKREFTELSE_TOPIC", stringType)] }

    val bekreftelseKafkaProdusent by lazy {
        val kafkaProducer =
            KafkaProducer(AivenConfig.default.producerConfig(Properties()), LongSerializer(), BekreftelseAvroSerializer()).also {
                Runtime.getRuntime().addShutdownHook(
                    Thread {
                        it.close()
                    },
                )
            }

        KafkaProdusent(kafkaProducer, bekreftelseTopic)
    }

    private val tokenXClient by lazy {
        val tokenX = OAuth2Config.TokenX(properties)
        CachedOauth2Client(
            tokenEndpointUrl = tokenX.tokenEndpointUrl,
            authType = tokenX.privateKey(),
        )
    }

    fun tokenXClient(audience: String) =
        { subjectToken: String ->
            tokenXClient
                .tokenExchange(
                    token = subjectToken,
                    audience = audience,
                ).access_token
        }

    private val azureAdConfig by lazy { OAuth2Config.AzureAd(properties) }

    private val azureAdClient by lazy {
        CachedOauth2Client(
            tokenEndpointUrl = azureAdConfig.tokenEndpointUrl,
            authType = azureAdConfig.clientSecret(),
        )
    }

    val defaultObjectMapper: ObjectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}
