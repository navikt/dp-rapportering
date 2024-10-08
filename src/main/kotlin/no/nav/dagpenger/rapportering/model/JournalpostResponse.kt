package no.nav.dagpenger.rapportering.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

// Se https://confluence.adeo.no/display/BOA/opprettJournalpost
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JournalpostResponse(
    // Dokumentasjon sier at dette feltet er String. Men det ser ut at vi får numerisk ID her
    val journalpostId: Long,
    val melding: String? = null,
    val journalpostferdigstilt: Boolean,
    val dokumenter: List<DokumentInfo>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class DokumentInfo(
    // Dokumentasjon sier at dette feltet er String. Men det ser ut at vi får numerisk ID her
    val dokumentInfoId: Long,
)
