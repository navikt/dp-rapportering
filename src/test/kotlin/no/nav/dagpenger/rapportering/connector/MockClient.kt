package no.nav.dagpenger.rapportering.connector

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

internal fun createMockClient(
    statusCode: Int,
    responseBody: String,
): HttpClientEngine =
    MockEngine {
        respond(
            content = responseBody,
            status = HttpStatusCode.fromValue(statusCode),
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }
