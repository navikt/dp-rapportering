package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.meldinger.SøknadInnsendtMelding
import no.nav.helse.rapids_rivers.MessageContext

internal interface IHendelseMediator {
    fun behandle(melding: SøknadInnsendtMelding, hendelse: SøknadInnsendtHendelse, context: MessageContext)
    fun behandle(hendelse: NyAktivitetHendelse)
    fun behandle(hendelse: SlettAktivitetHendelse)
}
