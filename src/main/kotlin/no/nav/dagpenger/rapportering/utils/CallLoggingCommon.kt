package no.nav.dagpenger.rapportering.utils

import java.util.UUID

fun generateCallId(): String = "dp-rapportering-${UUID.randomUUID()}"

fun headersToString(headers: List<String>): String {
    if (headers.size == 1) {
        return headers[0]
    }

    return headers.joinToString(",", "[", "]")
}
