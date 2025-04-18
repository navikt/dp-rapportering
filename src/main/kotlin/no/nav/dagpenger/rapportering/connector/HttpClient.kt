package no.nav.dagpenger.rapportering.connector

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import no.nav.dagpenger.rapportering.utils.OutgoingCallLoggingPlugin
import java.time.Duration

fun createHttpClient(engine: HttpClientEngine) =
    HttpClient(engine) {
        expectSuccess = false

        install(HttpTimeout) {
            connectTimeoutMillis = Duration.ofSeconds(60).toMillis()
            requestTimeoutMillis = Duration.ofSeconds(100).toMillis() // Adapter gjør 3 forsøk med 30s timeout + 10s
            socketTimeoutMillis = Duration.ofSeconds(60).toMillis()
        }

        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }

        install("OutgoingCallInterceptor") {
            OutgoingCallLoggingPlugin().intercept(this)
        }
    }
