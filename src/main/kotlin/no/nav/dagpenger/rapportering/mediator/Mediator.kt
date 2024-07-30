package no.nav.dagpenger.rapportering.mediator

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.model.Person
import no.nav.dagpenger.rapportering.model.hendelse.InnsendtPeriodeHendelse
import no.nav.dagpenger.rapportering.model.hendelse.PersonHendelse
import no.nav.dagpenger.rapportering.model.hendelse.SoknadInnsendtHendelse
import no.nav.helse.rapids_rivers.RapidsConnection

class Mediator(
    private val rapidsConnection: RapidsConnection,
) : HendelseMediator {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun behandle(hendelse: SoknadInnsendtHendelse) {
        logger.info { "Mottok SøknadInnsendtHendelse: $hendelse" }
    }

    override fun behandle(hendelse: InnsendtPeriodeHendelse) {
        logger.info { "Behandler InnsendtPeriodeHendelse: $hendelse" }
    }

    private fun <Hendelse: PersonHendelse> handter(hendelse: Hendelse) {

    }
}
