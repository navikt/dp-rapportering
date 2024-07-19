package no.nav.dagpenger.rapportering

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidsConnection
import java.time.LocalDateTime
import java.util.UUID

class Mediator(
    private val rapidsConnection: RapidsConnection,
) {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    fun behandle(hendelse: SoknadInnsendtHendelse) {
        logger.info { "Mottok SøknadInnsendtHendelse: $hendelse" }
    }
}

data class SoknadInnsendtHendelse(
    val meldingsreferanseId: UUID,
    val ident: String,
    internal val opprettet: LocalDateTime,
    internal val søknadId: UUID,
) : PersonHendelse(meldingsreferanseId, ident)

abstract class PersonHendelse(
    private val meldingsreferanseId: UUID,
    private val ident: String,
) {
    fun ident() = ident

    fun meldingsreferanseId() = meldingsreferanseId
}
