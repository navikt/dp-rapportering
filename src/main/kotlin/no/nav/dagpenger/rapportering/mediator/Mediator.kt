package no.nav.dagpenger.rapportering.mediator

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.rapportering.model.hendelse.InnsendtPeriodeHendelse
import no.nav.dagpenger.rapportering.model.hendelse.MeldingOmArbeidssokerNestePeriode
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
        val innsendtPeriodeMelding = MeldingOmPeriodeInnsendt(hendelse).asMessage().toJson()
        val arbeidssokerNestePeriodeMelding = MeldingOmArbeidssokerNestePeriode(hendelse).asMessage().toJson()
        withLoggingContext(
            "rapporteringsId" to hendelse.rapporteringsperiode.id.toString(),
        ) {
            logger.info { "Publiserer hendelse for innsendt rapporteringsperiode" }
            sikkerlogg.info { "Publiserer hendelse for innsendt rapporteringsperiode. Melding: $innsendtPeriodeMelding" }
            rapidsConnection.publish(hendelse.ident(), innsendtPeriodeMelding)

            logger.info { "Publiserer hendelse for arbeidssøker neste periode" }
            sikkerlogg.info { "Publiserer hendelse for arbeidssøker neste periode. Melding: $arbeidssokerNestePeriodeMelding" }
            rapidsConnection.publish(hendelse.ident(), arbeidssokerNestePeriodeMelding)
        }
    }
}
