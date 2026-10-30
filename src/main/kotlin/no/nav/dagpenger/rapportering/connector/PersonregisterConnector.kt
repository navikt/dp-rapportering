package no.nav.dagpenger.rapportering.connector

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.dagpenger.rapportering.config.Configuration
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.metrics.ActionTimer
import no.nav.dagpenger.rapportering.utils.Sikkerlogg

class PersonregisterConnector(
    val personregisterUrl: String = Configuration.personregisterUrl,
    val tokenXProvider: (String) -> String? = Configuration.tokenXClient(Configuration.personregisterAudience),
    val httpClient: HttpClient,
    actionTimer: ActionTimer,
) {
    private val logger = KotlinLogging.logger {}
    private val httpClientUtils = HttpClientUtils(httpClient, personregisterUrl, tokenXProvider, actionTimer)

    suspend fun hentPersonstatus(
        ident: String,
        subjectToken: String,
    ): Personstatus? =
        withContext(Dispatchers.IO) {
            val result =
                httpClientUtils
                    .get(
                        "/personstatus",
                        subjectToken,
                        "personregister-hentPersonstatus",
                    ).also {
                        logger.info { "Kall til personregister for å hente personstatus ga status ${it.status}" }
                        Sikkerlogg.info { "Kall til personregister for å hente personstatus for $ident ga status ${it.status}" }
                    }

            when (result.status) {
                HttpStatusCode.NotFound -> {
                    null
                }

                HttpStatusCode.OK -> {
                    result
                        .bodyAsText()
                        .let { defaultObjectMapper.readValue(it, Personstatus::class.java) }
                }

                else -> {
                    val melding = result.bodyAsText()
                    Sikkerlogg.error {
                        "Uforventet status ved henting av personstatus for $ident: ${result.status.value} - $melding"
                    }
                    throw RuntimeException(
                        "Uforventet status ved henting av personstatus: ${result.status.value}",
                    )
                }
            }
        }

    suspend fun hentBrukerstatus(
        ident: String,
        subjectToken: String,
    ): Brukerstatus =
        withContext(Dispatchers.IO) {
            val personstatus = hentPersonstatus(ident, subjectToken)

            personstatus?.status ?: Brukerstatus.IKKE_DAGPENGERBRUKER
        }
}

data class Personstatus(
    val ident: String,
    val status: Brukerstatus,
    val overtattBekreftelse: Boolean? = false,
    val ansvarligSystem: AnsvarligSystem?,
    val erRegistrertArbeidssøker: Boolean? = false,
)

fun Personstatus?.erBekreftelseOvertatt(): Boolean = this?.overtattBekreftelse ?: false

enum class Brukerstatus {
    DAGPENGERBRUKER,
    IKKE_DAGPENGERBRUKER,
}

enum class AnsvarligSystem {
    ARENA,
    DP,
}
