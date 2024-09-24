package no.nav.dagpenger.rapportering.api

import com.fasterxml.jackson.core.type.TypeReference
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import java.time.LocalDate

val objectMapper = defaultObjectMapper

suspend fun HttpClient.doPost(
    urlString: String,
    token: String? = null,
    body: Any? = null,
): HttpResponse =
    this.post(urlString) {
        header(HttpHeaders.Accept, ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        if (token != null) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (body != null) {
            setBody(objectMapper.writeValueAsString(body))
        }
    }

suspend inline fun <reified T> HttpClient.doPostAndReceive(
    urlString: String,
    token: String? = null,
    body: Any? = null,
): Response<T> =
    this.doPost(urlString, token, body).let {
        Response(it, objectMapper.readValue(it.bodyAsText(), object : TypeReference<T>() {}))
    }

suspend fun HttpClient.doGet(
    urlString: String,
    token: String? = null,
    extraHeaders: List<Pair<String, String>> = emptyList(),
): HttpResponse =
    this.get(urlString) {
        header(HttpHeaders.Accept, ContentType.Application.Json)
        if (token != null) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (extraHeaders.isNotEmpty()) {
            extraHeaders.forEach { (key, value) -> header(key, value) }
        }
    }

suspend inline fun <reified T> HttpClient.doGetAndReceive(
    urlString: String,
    token: String? = null,
    extraHeaders: List<Pair<String, String>> = emptyList(),
): Response<T> =
    this
        .doGet(urlString, token, extraHeaders)
        .let { Response(it, objectMapper.readValue(it.bodyAsText(), object : TypeReference<T>() {})) }

suspend fun HttpClient.doDelete(
    urlString: String,
    token: String? = null,
    body: Any? = null,
): HttpResponse =
    this.delete(urlString) {
        header(HttpHeaders.Accept, ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        if (token != null) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (body != null) {
            setBody(objectMapper.writeValueAsString(body))
        }
    }

data class Response<T>(
    val httpResponse: HttpResponse,
    val body: T,
)

fun rapporteringsperiodeFor(
    id: Long = 123L,
    fraOgMed: LocalDate = LocalDate.now().minusDays(13),
    tilOgMed: LocalDate = fraOgMed.plusDays(13),
    aktivitet: Aktivitet? = null,
    kanSendes: Boolean = true,
    kanEndres: Boolean = true,
    status: RapporteringsperiodeStatus = TilUtfylling,
    bruttoBelop: String? = null,
    registrertArbeidssoker: Boolean? = null,
    begrunnelseEndring: String? = null,
    originalId: Long? = null,
    rapporteringstype: String? = null,
) = Rapporteringsperiode(
    id = id,
    periode = Periode(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
    dager =
        (0..13).map {
            Dag(
                dato = fraOgMed.plusDays(it.toLong()),
                aktiviteter = aktivitet?.let { listOf(aktivitet) } ?: emptyList(),
                dagIndex = it,
            )
        },
    kanSendesFra = tilOgMed.minusDays(1),
    kanSendes = kanSendes,
    kanEndres = kanEndres,
    status = status,
    bruttoBelop = bruttoBelop?.toDouble(),
    registrertArbeidssoker = registrertArbeidssoker,
    begrunnelseEndring = begrunnelseEndring,
    originalId = originalId,
    rapporteringstype = rapporteringstype,
)
