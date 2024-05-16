package no.nav.dagpenger.rapportering.connector

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

internal fun createMockClient(
    statusCode: Int,
    responseBody: Any,
): HttpClientEngine {
    val mockEngine =
        MockEngine {
            respond(
                content = objectMapper.writeValueAsString(responseBody),
                status = HttpStatusCode.fromValue(statusCode),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    return mockEngine
}

val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
