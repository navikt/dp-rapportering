package no.nav.dagpenger.rapportering.mediator

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.rapportering.model.hendelse.InnsendtPeriodeHendelse
import no.nav.dagpenger.rapportering.model.hendelse.SoknadInnsendtHendelse
import no.nav.helse.rapids_rivers.JsonMessage
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
        val melding =
            JsonMessage.newMessage(
                "rapporteringsperiode_innsendt_hendelse",
                mapOf(
                    "ident" to hendelse.ident,
                    "rapporteringsId" to hendelse.rapporteringsperiodeId,
                    "fom" to hendelse.periode.fraOgMed,
                    "tom" to hendelse.periode.tilOgMed,
                    // "dager" to hendelse.dager,
                ),
            )
        withLoggingContext(
            "rapporteringsId" to hendelse.rapporteringsperiodeId.toString(),
        ) {
            logger.info { "Publiserer hendelse for innsendt rapporteringsperiode" }
            sikkerlogg.info { "Publiserer hendelse for innsendt rapporteringsperiode. Melding: ${melding.toJson()}" }

            rapidsConnection.publish(hendelse.ident(), melding.toJson())
        }
    }
}
