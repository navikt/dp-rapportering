package no.nav.dagpenger.rapportering.api

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.dagpenger.rapportering.module
import no.nav.dagpenger.rapportering.repository.Postgres.database
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

open class ApiTestSetup {
    companion object {
        const val TOKENX_ISSUER_ID = "tokenx"
        const val AZURE_ISSUER_ID = "azure"
        const val REQUIRED_AUDIENCE = "tokenx"
        val TEST_PRIVATE_JWK =
            """
            {
                "kty":"RSA",
                "alg":"RS256",
                "use":"sig",
                "p":"_xCPvqs85ZZVg460Qfot26rQoNRPTOVDo5p4nqH3ep6BK_5TvoU5LFXd26W-1V1Lc5fcvvftClPOT201xgat4DVtliNtoc8od_tWr190A3AzbsAVFOx0nKa5uhLBxP9SsPM84llp6PXF6QTMGFiPYuoLDaQQqL1K4BbHq3ZzF2M",
                "q":"7QLqW75zkfSDrn5rMoF50WXyB_ysNx6-2SvaXKGXaOn80IR7QW5vwkleJnsdz_1kr04rJws2p4HBJjUFfSJDi1Dapj7tbIwb0a1szDs6Y2fAa3DlzgXZCkoE2TIrW6UITgs14pI_a7RasclE71FpoZ78XNBvj3NmZugkNLBvRjs",
                "d":"f7aT4poed8uKdcSD95mvbfBdb6X-M86d99su0c390d6gWwYudeilDugH9PMwqUeUhY0tdaRVXr6rDDIKLSE-uEyaYKaramev0cG-J_QWYJU2Lx-4vDGNHAE7gC99o1Ee_LXqMDCBawMYyVcSWx7PxGQfzhSsARsAIbkarO1sg9zsqPS4exSMbK8wyCTPgRbnkB32_UdZSGbdSib1jSYyyoAItZ8oZHiltVsZIlA97kS4AGPtozde043NC7Ik0uEzgB5qJ_tR7vW8MfDrBj6da2NrLh0UH-q28dooBO1vEu0rvKZIescXYk9lk1ZakHhhpZaLykDOGzxCpronzP3_kQ",
                "e":"AQAB",
                "qi":"9kMIR6pEoiwN3M6O0n8bnh6c3KbLMoQQ1j8_Zyir7ZIlmRpWYl6HtK0VnD88zUuNKTrQa7-jfE5uAUa0PubzfRqybACb4S3HIAuSQP00_yCPzCSRrbpGRDFqq-8eWVwI9VdiN4oqkaaWcL1pd54IDcHIbfk-ZtNtZgsOlodeRMo",
                "dp":"VUecSAvI2JpjDRFxg326R2_dQWi6-uLMsq67FY7hx8WnOqZWKaUxcHllLENGguAmkgd8bv1F6-YJXNUO3Z7uE8DJWyGNTkSNK1CFsy0fBOdGywi-A7jrZFT6VBRhZRRY-YDaInPyzUkfWsGX26wAhPnrqCvqxgBEQJhdOh7obDE",
                "dq":"7EUfw92T8EhEjUrRKkQQYEK0iGnGdBxePLiOshEUky3PLT8kcBHbr17cUJgjHBiKqofOVNnE3i9nkOMCWcAyfUtY7KmGndL-WIP-FYplpnrjQzgEnuENgEhRlQOCXZWjNcnPKdKJDqF4WAtAgSIznz6SbSQMUoDD8IoyraPFCck",
                "n":"7CU8tTANiN6W_fD9SP1dK2vQvCkf7-nwvBYe5CfANV0_Bb0ZmQb77FVVsl1beJ7EYLz3cJmL8Is1RCHKUK_4ydqihNjEWTyZiQoj1i67pkqk_zRvfQa9raZR4uZbuBxx7dWUoPC6fFH2F_psAlHW0zf90fsLvhB6Aqq3uvO7XXqo8qNl9d_JSG0Rg_2QUYVb0WKmPVbbhgwtkFu0Tyuev-VZ9IzTbbr5wmZwEUVY7YAi73pDJkcZt5r2WjOF_cuIXe-O2vwbOrRgmJfHO9--mVLdATnEyrb6q2oy_75h6JjP-R4-TD1hyoFFoE2gmj-kSS6Z_Gggljs3Aw7--Nh10Q"
            }
            """.trimIndent()

        val mockOAuth2Server = MockOAuth2Server()

        @BeforeAll
        @JvmStatic
        fun setup() {
            try {
                println("Start mockserver")
                mockOAuth2Server.start(8091)
            } catch (e: Exception) {
                println("Failed to start mockserver")
                mockOAuth2Server.shutdown()
                mockOAuth2Server.start(8092)
            }
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            println("Stopper mockserver")
            mockOAuth2Server.shutdown()
        }
    }

    fun setUpTestApplication(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            environment {
                config = setEnvConfig()
            }

            val httpClient = client
            application {
                module(httpClient)
            }

            block()
        }
    }

    private fun setEnvConfig(): MapApplicationConfig {
        System.setProperty("MELDEPLIKT_ADAPTER_HOST", "meldeplikt-adapter")
        System.setProperty("MELDEPLIKT_ADAPTER_AUDIENCE", REQUIRED_AUDIENCE)
        System.setProperty("DOKARKIV_HOST", "dokarkiv")
        System.setProperty("DOKARKIV_AUDIENCE", REQUIRED_AUDIENCE)
        System.setProperty("DB_JDBC_URL", "${database.jdbcUrl}&user=${database.username}&password=${database.password}")
        System.setProperty("token-x.client-id", TOKENX_ISSUER_ID)
        System.setProperty("TOKEN_X_CLIENT_ID", TOKENX_ISSUER_ID)
        System.setProperty("TOKEN_X_PRIVATE_JWK", TEST_PRIVATE_JWK)
        System.setProperty("token-x.well-known-url", mockOAuth2Server.wellKnownUrl(TOKENX_ISSUER_ID).toString())
        System.setProperty("TOKEN_X_WELL_KNOWN_URL", mockOAuth2Server.wellKnownUrl(TOKENX_ISSUER_ID).toString())
        System.setProperty("azure-app.well-known-url", mockOAuth2Server.wellKnownUrl(AZURE_ISSUER_ID).toString())
        System.setProperty("AZURE_APP_WELL_KNOWN_URL", mockOAuth2Server.wellKnownUrl(AZURE_ISSUER_ID).toString())
        System.setProperty("AZURE_APP_CLIENT_ID", AZURE_ISSUER_ID)
        System.setProperty("AZURE_APP_CLIENT_SECRET", TEST_PRIVATE_JWK)

        return MapApplicationConfig(
            "no.nav.security.jwt.issuers.size" to "1",
            "no.nav.security.jwt.issuers.0.issuer_name" to TOKENX_ISSUER_ID,
            "no.nav.security.jwt.issuers.0.discoveryurl" to mockOAuth2Server.wellKnownUrl(TOKENX_ISSUER_ID).toString(),
            "no.nav.security.jwt.issuers.0.accepted_audience" to REQUIRED_AUDIENCE,
            "ktor.environment" to "local",
        )
    }

    fun issueToken(ident: String): String =
        mockOAuth2Server
            .issueToken(
                TOKENX_ISSUER_ID,
                "myclient",
                DefaultOAuth2TokenCallback(
                    audience = listOf(REQUIRED_AUDIENCE),
                    claims = mapOf("pid" to ident, "acr" to "Level4"),
                ),
            ).serialize()
}
