package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.meldinger.SøknadInnsendtMelding
import no.nav.helse.rapids_rivers.MessageContext

internal interface IHendelseMediator {
    fun behandle(nyAktivitetHendelse: NyAktivitetHendelse)
    fun behandle(melding: SøknadInnsendtMelding, hendelse: SøknadInnsendtHendelse, context: MessageContext)
}
