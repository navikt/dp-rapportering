package no.nav.dagpenger.rapportering.model

import com.fasterxml.jackson.module.kotlin.convertValue
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import java.time.LocalDate

data class PeriodeData(
    val id: Long,
    val ident: String,
    val periode: Periode,
    val dager: List<Dag>,
    val kanSendesFra: LocalDate,
    val opprettetAv: OpprettetAv,
    val kilde: Kilde,
    val type: Type,
    val status: String = "Innsendt",
    val mottattDato: LocalDate,
    // Refererer til originalt meldekort ved korrigering
    val originalId: Long?,
) {
    enum class OpprettetAv {
        Arena,
        Dagpenger,
    }

    data class Kilde(
        val rolle: Rolle,
        val ident: String,
    )

    enum class Rolle {
        Bruker,
        Saksbehandler,
    }

    enum class Type {
        Original,
        Korrigert,
    }
}

fun PeriodeData.toMap() = defaultObjectMapper.convertValue<Map<String, Any>>(this)
