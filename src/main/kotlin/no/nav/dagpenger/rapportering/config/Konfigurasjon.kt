package no.nav.dagpenger.rapportering.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.http.HttpHeaders
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.doublereceive.DoubleReceive
import io.ktor.server.request.path
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.api.auth.AuthFactory.azureAd
import no.nav.dagpenger.rapportering.api.auth.AuthFactory.tokenX
import no.nav.dagpenger.rapportering.config.Configuration.MDC_CORRELATION_ID
import no.nav.dagpenger.rapportering.config.Configuration.NO_LOG_PATHS
import no.nav.dagpenger.rapportering.config.Configuration.config
import no.nav.dagpenger.rapportering.repository.KallLoggRepository
import no.nav.dagpenger.rapportering.repository.KallLoggRepositoryPostgres
import no.nav.dagpenger.rapportering.repository.PostgresDataSourceBuilder.clean
import no.nav.dagpenger.rapportering.repository.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.repository.PostgresDataSourceBuilder.runMigration
import no.nav.dagpenger.rapportering.utils.IncomingCallLoggingPlugin
import no.nav.dagpenger.rapportering.utils.generateCallId
import org.flywaydb.core.internal.configuration.ConfigUtils
import org.slf4j.event.Level

fun Application.konfigurasjon(
    prometheusMeterRegistry: PrometheusMeterRegistry,
    kallLoggRepository: KallLoggRepository = KallLoggRepositoryPostgres(dataSource),
    auth: AuthenticationConfig.() -> Unit = {
        jwt("tokenX") { tokenX() }
        jwt("azureAd") { azureAd() }
    },
) {
    val logger = KotlinLogging.logger {}

    if (config[ConfigUtils.CLEAN_DISABLED] == "false") {
        logger.info { "Cleaning database" }
        clean()
    }

    runMigration()

    install(MicrometerMetrics) {
        registry = prometheusMeterRegistry
        meterBinders = listOf(
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            JvmThreadMetrics(),
            ProcessorMetrics()
        )
    }

    install(DoubleReceive) {
    }

    install(CallId) {
        // Retrieve the callId from a headerName
        // Automatically updates the response with the callId in the specified headerName
        header(HttpHeaders.XRequestId)

        // If it can't retrieve a callId from the ApplicationCall, it will try the generate-blocks coalescing until one of them is not null
        generate { generateCallId() }

        // Once a callId is generated, this optional function is called to verify if the retrieved or generated callId String is valid
        verify { callId: String ->
            callId.isNotEmpty()
        }
    }

    install(CallLogging) {
        disableDefaultColors()
        filter {
            it.request.path() !in NO_LOG_PATHS
        }
        level = Level.INFO

        // Put callId into MDC
        callIdMdc(MDC_CORRELATION_ID)
    }

    install(IncomingCallLoggingPlugin) {
        this.kallLoggRepository = kallLoggRepository
    }

    install(Authentication) {
        auth()
    }

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            registerModule(
                KotlinModule
                    .Builder()
                    .configure(KotlinFeature.NullToEmptyCollection, true)
                    .configure(KotlinFeature.NullToEmptyMap, true)
                    .configure(KotlinFeature.NullIsSameAsDefault, true)
                    .configure(KotlinFeature.SingletonSupport, true)
                    .configure(KotlinFeature.StrictNullChecks, false)
                    .build(),
            )
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
}
