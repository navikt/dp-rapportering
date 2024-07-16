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
import no.nav.dagpenger.oauth2.CachedOauth2Client
import no.nav.dagpenger.oauth2.OAuth2Config

internal object Configuration {
    const val APP_NAME = "dp-rapportering"

    const val MDC_CORRELATION_ID = "correlationId"

    val NO_LOG_PATHS = setOf("/metrics", "/isAlive", "/isReady")

    private val defaultProperties =
        ConfigurationMap(
            mapOf(
                "beregningsdato_strategi" to "tom",
                "Grupper.saksbehandler" to "123",
                "RAPID_APP_NAME" to "dp-rapportering",
                "KAFKA_CONSUMER_GROUP_ID" to "dp-rapportering-v1",
                "KAFKA_RAPID_TOPIC" to "teamdagpenger.rapid.v1",
                "KAFKA_RESET_POLICY" to "latest",
                "KAFKA_BOOTSTRAP_SERVERS" to "127.0.0.1:9092",
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

    val dokarkivUrl by lazy {
        properties[Key("DOKARKIV_HOST", stringType)].let {
            "https://$it"
        }
    }

    val dokarkivAudience by lazy { properties[Key("DOKARKIV_AUDIENCE", stringType)] }

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
                ).accessToken
        }

    private val azureAd by lazy {
        val aad = OAuth2Config.AzureAd(properties)
        CachedOauth2Client(
            tokenEndpointUrl = aad.tokenEndpointUrl,
            authType = aad.clientSecret(),
        )
    }

    fun azureADClient() =
        { audience: String ->
            azureAd
                .clientCredentials(audience)
                .accessToken
        }

    val defaultObjectMapper: ObjectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}
