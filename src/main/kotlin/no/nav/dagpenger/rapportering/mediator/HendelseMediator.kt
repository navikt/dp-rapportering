package no.nav.dagpenger.rapportering.mediator

import no.nav.dagpenger.rapportering.model.hendelse.InnsendtPeriodeHendelse
import no.nav.dagpenger.rapportering.model.hendelse.SoknadInnsendtHendelse

internal interface HendelseMediator {
    fun behandle(hendelse: SoknadInnsendtHendelse)

    fun behandle(hendelse: InnsendtPeriodeHendelse)
}
