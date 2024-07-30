package no.nav.dagpenger.rapportering.model.hendelse

import java.time.LocalDateTime
import java.util.UUID

data class SoknadInnsendtHendelse(
    val meldingsreferanseId: UUID,
    val ident: String,
    internal val opprettet: LocalDateTime,
    internal val søknadId: UUID,
) : PersonHendelse(meldingsreferanseId, ident)
