package no.nav.dagpenger.rapportering.utils

import com.auth0.jwt.JWT
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

fun generateCallId(): String = "dp-rapp-${UUIDv7.newUuid()}"

fun headersToString(headers: List<String>): String {
    if (headers.size == 1) {
        return headers[0]
    }

    return headers.joinToString(",", "[", "]")
}

fun getIdent(headers: Headers): String {
    try {
        val authHeader = headers[HttpHeaders.Authorization] ?: ""
        val token = authHeader.replace("Bearer ", "")
        val jwt = JWT.decode(token)
        val pid = jwt.getClaim("pid")
        val sub = jwt.getClaim("sub")

        return if (!pid.isNull) {
            pid.asString()
        } else if (!sub.isNull) {
            sub.asString()
        } else {
            ""
        }
    } catch (_: Exception) {
        return ""
    }
}
