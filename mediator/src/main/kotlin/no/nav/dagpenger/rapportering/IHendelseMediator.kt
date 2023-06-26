package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.KorrigerPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.ManuellInnsendingHendelse
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringssyklusHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringsfristHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringspliktDatoHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.hendelser.VedtakAvslåttHendelse
import no.nav.dagpenger.rapportering.hendelser.VedtakInnvilgetHendelse

internal interface IHendelseMediator {
    fun behandle(hendelse: SøknadInnsendtHendelse)
    fun behandle(hendelse: NyAktivitetHendelse)
    fun behandle(hendelse: SlettAktivitetHendelse)
    fun behandle(hendelse: GodkjennPeriodeHendelse)
    fun behandle(hendelse: RapporteringsfristHendelse)
    fun behandle(hendelse: NyRapporteringssyklusHendelse)
    fun behandle(hendelse: KorrigerPeriodeHendelse)
    fun behandle(hendelse: ManuellInnsendingHendelse)
    fun behandle(hendelse: RapporteringspliktDatoHendelse)
    fun behandle(hendelse: VedtakInnvilgetHendelse)
    fun behandle(hendelse: VedtakAvslåttHendelse)
}
