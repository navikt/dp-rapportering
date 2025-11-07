package no.nav.dagpenger.rapportering.jobs

import io.github.oshai.kotlinlogging.KLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking

fun isLeader(
    httpClient: HttpClient,
    logger: KLogger,
): Boolean {
    return try {
        val electorUrl = System.getenv("ELECTOR_GET_URL")
        val hostname = System.getenv("HOSTNAME")

        if (hostname == null || electorUrl == null || hostname.isEmpty() || electorUrl.isEmpty()) {
            logger.error { "Kunne ikke sjekke leader HOSTNAME og/eller ELECTOR_GET_URL er ikke definert" }
            return true // Det er bedre å få flere pod'er til å starte jobben enn ingen
        }

        hostname ==
            runBlocking {
                httpClient.get(electorUrl).body<Leader>().name
            }
    } catch (e: Exception) {
        logger.error(e) { "Kunne ikke sjekke leader" }
        true // Det er bedre å få flere pod'er til å starte jobben enn ingen
    }
}

private data class Leader(
    val name: String,
)
