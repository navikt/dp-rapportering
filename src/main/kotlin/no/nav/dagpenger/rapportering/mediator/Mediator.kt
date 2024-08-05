package no.nav.dagpenger.rapportering.mediator

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.rapportering.model.hendelse.InnsendtPeriodeHendelse
import no.nav.dagpenger.rapportering.model.hendelse.MeldingOmPeriodeInnsendt
import no.nav.dagpenger.rapportering.model.hendelse.SoknadInnsendtHendelse
import no.nav.helse.rapids_rivers.RapidsConnection

class Mediator(
    private val rapidsConnection: RapidsConnection,
) : HendelseMediator {
    private companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall.Mediator")
    }

    override fun behandle(hendelse: SoknadInnsendtHendelse) {
        logger.info { "Mottok SøknadInnsendtHendelse: $hendelse" }
    }

    override fun behandle(hendelse: InnsendtPeriodeHendelse) {
        logger.info { "Behandler InnsendtPeriodeHendelse: $hendelse" }
        val melding = MeldingOmPeriodeInnsendt(hendelse).asMessage().toJson()
        withLoggingContext(
            "rapporteringsId" to hendelse.rapporteringsperiodeId.toString(),
        ) {
            logger.info { "Publiserer hendelse for innsendt rapporteringsperiode" }
            sikkerlogg.info { "Publiserer hendelse for innsendt rapporteringsperiode. Melding: $melding" }

            rapidsConnection.publish(hendelse.ident(), melding)
        }
    }
}
