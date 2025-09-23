package no.nav.dagpenger.rapportering.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.PropertyGroup
import com.natpryce.konfig.getValue
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import io.getunleash.DefaultUnleash
import io.getunleash.util.UnleashConfig
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.oauth2.CachedOauth2Client
import no.nav.dagpenger.oauth2.OAuth2Config
import no.nav.dagpenger.rapportering.kafka.KafkaSchemaRegistryConfig
import no.nav.dagpenger.rapportering.kafka.KafkaServerKonfigurasjon
import java.time.ZoneId
import java.util.UUID

internal object Configuration {
    const val APP_NAME = "dp-rapportering"

    const val MDC_CORRELATION_ID = "correlationId"

    val NO_LOG_PATHS = setOf("/metrics", "/isAlive", "/isReady")

    val ZONE_ID = ZoneId.of("Europe/Oslo")

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

    val personregisterUrl by lazy {
        properties[Key("PERSONREGISTER_HOST", stringType)].let {
            "https://$it"
        }
    }

    val personregisterAudience by lazy { properties[Key("PERSONREGISTER_AUDIENCE", stringType)] }

    val personregisterTokenProvider: () -> String by lazy {
        {
            runBlocking {
                azureAdClient
                    .clientCredentials(
                        "api://" +
                            properties[Key("PERSONREGISTER_SCOPE", stringType)].replace(":", ".") +
                            "/.default",
                    ).access_token ?: throw RuntimeException("Failed to get Personregister Azure token")
            }
        }
    }

    val meldekortregisterUrl by lazy {
        properties[Key("MELDEKORTREGISTER_HOST", stringType)].let {
            "https://$it"
        }
    }

    val meldekortregisterAudience by lazy { properties[Key("MELDEKORTREGISTER_AUDIENCE", stringType)] }

    val pdlUrl by lazy {
        properties[Key("PDL_API_HOST", stringType)].let {
            "https://$it/graphql"
        }
    }

    val pdlApiTokenProvider: () -> String by lazy {
        {
            runBlocking {
                azureAdClient
                    .clientCredentials(properties[Key("PDL_API_SCOPE", stringType)])
                    .access_token ?: throw RuntimeException("Failed to get token")
            }
        }
    }

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
        properties[Key("ARBEIDSSOKERREGISTER_OPPSLAG_URL", stringType)]
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

    val kafkaServerKonfigurasjon =
        KafkaServerKonfigurasjon(
            autentisering = "SSL",
            kafkaBrokers = properties[Key("KAFKA_BROKERS", stringType)],
            keystorePath = properties.getOrNull(Key("KAFKA_KEYSTORE_PATH", stringType)),
            credstorePassword = properties.getOrNull(Key("KAFKA_CREDSTORE_PASSWORD", stringType)),
            truststorePath = properties.getOrNull(Key("KAFKA_TRUSTSTORE_PATH", stringType)),
        )

    val kafkaSchemaRegistryConfig =
        KafkaSchemaRegistryConfig(
            url = properties[Key("KAFKA_SCHEMA_REGISTRY", stringType)],
            username = properties[Key("KAFKA_SCHEMA_REGISTRY_USER", stringType)],
            password = properties[Key("KAFKA_SCHEMA_REGISTRY_PASSWORD", stringType)],
            autoRegisterSchema = true,
            avroSpecificReaderConfig = true,
        )

    val bekreftelseTopic by lazy { properties[Key("BEKREFTELSE_TOPIC", stringType)] }

    private val unleashConfig by lazy {
        UnleashConfig
            .builder()
            .fetchTogglesInterval(5)
            .appName(properties.getOrElse(Key("NAIS_APP_NAME", stringType), UUID.randomUUID().toString()))
            .instanceId(properties.getOrElse(Key("NAIS_CLIENT_ID", stringType), UUID.randomUUID().toString()))
            .unleashAPI(properties[Key("UNLEASH_SERVER_API_URL", stringType)] + "/api")
            .apiKey(properties[Key("UNLEASH_SERVER_API_TOKEN", stringType)])
            .environment(properties[Key("UNLEASH_SERVER_API_ENV", stringType)])
            .build()
    }

    val unleash by lazy {
        DefaultUnleash(unleashConfig)
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
