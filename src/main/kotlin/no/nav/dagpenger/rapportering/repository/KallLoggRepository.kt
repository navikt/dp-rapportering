package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.model.KallLogg

interface KallLoggRepository {
    fun lagreKallLogg(kallLogg: KallLogg): Long

    fun lagreResponse(
        kallLoggId: Long,
        status: Int,
        response: String,
    )

    fun hentKallLoggFelterListeByKorrelasjonId(korrelasjonId: String): List<KallLogg>
}
