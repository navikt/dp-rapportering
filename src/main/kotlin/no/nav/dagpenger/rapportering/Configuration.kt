package no.nav.dagpenger.rapportering

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.PropertyGroup
import com.natpryce.konfig.getValue
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

internal object Configuration {
    const val APP_NAME = "dp-rapportering"
    private val defaultProperties =
        ConfigurationMap(
            mapOf(
                "beregningsdato_strategi" to "tom",
                "Grupper.saksbehandler" to "123",
            ),
        )

    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    object Grupper : PropertyGroup() {
        val saksbehandler by stringType
    }

    val properties =
        ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding defaultProperties
    val config: Map<String, String> =
        properties.list().reversed().fold(emptyMap()) { map, pair ->
            map + pair.second
        }
    internal val beregningsdato_strategi by stringType

    val meldepliktAdapterUrl by lazy {
        properties[Key("MELDEPLIKT_ADAPTER_HOST", stringType)].let {
            "https://$it"
        }
    }
}
