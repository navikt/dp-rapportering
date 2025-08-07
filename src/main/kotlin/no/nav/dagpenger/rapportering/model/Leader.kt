package no.nav.dagpenger.rapportering.model

import com.fasterxml.jackson.annotation.JsonProperty

data class Leader(
    val name: String,
    @param:JsonProperty("last_update")
    val lastUpdate: String,
)
