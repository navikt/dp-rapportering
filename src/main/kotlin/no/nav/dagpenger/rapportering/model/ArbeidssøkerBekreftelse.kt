package no.nav.dagpenger.rapportering.model

import no.nav.dagpenger.rapportering.utils.tilMillis
import java.time.LocalDateTime
import java.util.UUID

data class ArbeidssøkerBekreftelse(
    val periodeId: String,
    val id: UUID,
    val bekreftelsesLøsning: BekreftelsesLøsning = BekreftelsesLøsning.DAGPENGER,
    val svar: Svar,
) {
    data class Svar(
        val sendtInnAv: Metadata = Metadata(),
        val gjelderFra: Long,
        val gjelderTil: Long,
        val harJobbetIDennePerioden: Boolean,
        val vilFortsetteSomArbeidssoeker: Boolean,
    )

    data class Metadata(
        val tidspunkt: Long = LocalDateTime.now().tilMillis(),
        val utfoertAv: Bruker = Bruker("SYSTEM", BekreftelsesLøsning.DAGPENGER.name),
        val kilde: String = BekreftelsesLøsning.DAGPENGER.name,
        val aarsak: String = "Bruker sendte inn dagpengermeldekort",
    )

    // Er det sluttbruker eller system som bekrefter?
    data class Bruker(
        val type: String = "SLUTTBRUKER",
        val id: String,
    )
}

enum class BekreftelsesLøsning {
    DAGPENGER,
}
