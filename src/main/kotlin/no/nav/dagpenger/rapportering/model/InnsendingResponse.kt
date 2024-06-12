package no.nav.dagpenger.rapportering.model

data class InnsendingResponse(
    val id: Long,
    val status: String,
    val feil: List<InnsendingFeil>,
)

data class InnsendingFeil(
    val kode: String,
    val params: List<String>? = null,
)
