package no.nav.dagpenger.rapportering.model

data class RecordKeyRequestBody(
    val ident: String,
)

data class RecordKeyResponse(
    val key: Long,
)
