package no.nav.dagpenger.rapportering.model

import java.time.LocalDateTime

data class KallLogg(
    val korrelasjonId: String,
    val tidspunkt: LocalDateTime?,
    val type: String,
    val kallRetning: String,
    val method: String,
    val operation: String,
    val status: Int,
    val kallTid: Long,
    val request: String,
    val response: String,
    val ident: String,
    val logginfo: String,
)
