package no.nav.dagpenger.rapportering.api

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.security.mock.oauth2.MockOAuth2Server

object TestApplication {
    private const val TOKENX_ISSUER_ID = "tokenx"
    private const val AZUREAD_ISSUER_ID = "azureAd"
    private const val CLIENT_ID = "dp-soknad"
    const val defaultDummyFodselsnummer = "12345"

    private val mockOAuth2Server: MockOAuth2Server by lazy {
        MockOAuth2Server().also { server ->
            server.start()
        }
    }

    internal val testTokenXToken: String by lazy {
        mockOAuth2Server.issueToken(
            issuerId = TOKENX_ISSUER_ID,
            audience = CLIENT_ID,
            claims = mapOf("pid" to defaultDummyFodselsnummer),
        ).serialize()
    }

    internal val testAzureAdToken: String by lazy {
        mockOAuth2Server.issueToken(
            issuerId = AZUREAD_ISSUER_ID,
            audience = CLIENT_ID,
            claims = mapOf("NAVident" to "123"),
        ).serialize()
    }

    internal fun withMockAuthServerAndTestApplication(
        moduleFunction: Application.() -> Unit,
        test: suspend ApplicationTestBuilder.() -> Unit,
    ) {
        System.setProperty("token-x.client-id", CLIENT_ID)
        System.setProperty("token-x.well-known-url", "${mockOAuth2Server.wellKnownUrl(TOKENX_ISSUER_ID)}")

        System.setProperty("azure-app.client-id", CLIENT_ID)
        System.setProperty("azure-app.well-known-url", "${mockOAuth2Server.wellKnownUrl(AZUREAD_ISSUER_ID)}")

        return testApplication {
            application(moduleFunction)
            test()
        }
    }

    internal fun HttpRequestBuilder.autentisert(token: String = testTokenXToken) {
        this.header(HttpHeaders.Authorization, "Bearer $token")
    }

    internal suspend fun ApplicationTestBuilder.autentisert(
        endepunkt: String,
        token: String = testTokenXToken,
        httpMethod: HttpMethod = HttpMethod.Get,
        body: String? = null,
    ): HttpResponse {
        return client.request(endepunkt) {
            this.method = httpMethod
            body?.let { this.setBody(TextContent(it, ContentType.Application.Json)) }
            this.header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}
