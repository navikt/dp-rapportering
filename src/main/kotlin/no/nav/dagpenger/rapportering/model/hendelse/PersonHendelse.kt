package no.nav.dagpenger.rapportering.model.hendelse

import java.util.UUID

abstract class PersonHendelse(
    private val meldingsreferanseId: UUID,
    private val ident: String,
) {
    fun ident() = ident

    fun meldingsreferanseId() = meldingsreferanseId
}
