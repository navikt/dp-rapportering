package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse

internal interface IHendelseMediator {
    fun behandle(hendelse: SøknadInnsendtHendelse)
    fun behandle(hendelse: NyAktivitetHendelse)
    fun behandle(hendelse: SlettAktivitetHendelse)
    fun behandle(godkjennPeriodeHendelse: GodkjennPeriodeHendelse)
}
