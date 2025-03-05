package no.nav.dagpenger.rapportering.jobs

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import mu.KLogger
import no.nav.dagpenger.rapportering.model.Leader
import java.net.InetAddress

fun isLeader(
    httpClient: HttpClient,
    logger: KLogger,
): Boolean {
    val hostname: String = InetAddress.getLocalHost().hostName

    val leader: String =
        try {
            val electorUrl = System.getenv("ELECTOR_GET_URL")

            runBlocking {
                val leaderJson: Leader = httpClient.get(electorUrl).body()
                leaderJson.name
            }
        } catch (e: Exception) {
            logger.error(e) { "Kunne ikke sjekke leader" }
            return true // Det er bedre å få flere pod'er til å starte jobben enn ingen
        }

    return hostname == leader
}
