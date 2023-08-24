package no.nav.dagpenger.rapportering.helpers

import no.nav.dagpenger.rapportering.Person
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringssyklusHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringspliktDatoHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.hendelser.VedtakInnvilgetHendelse
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal object TestData {
    val testIdent = "01010125255"
    val testPerson get() = Person(testIdent)

    fun søknadInnsendtHendelse() =
        SøknadInnsendtHendelse(UUID.randomUUID(), testIdent, LocalDateTime.now(), UUID.randomUUID())

    fun nyAktivitetHendelse(rapporteringsperiodeId: UUID, aktivitet: Aktivitet) =
        NyAktivitetHendelse(testIdent, rapporteringsperiodeId, aktivitet)

    fun nyAktivitetHendelse(rapporteringsperiodeId: UUID, dato: LocalDate) =
        nyAktivitetHendelse(rapporteringsperiodeId, Aktivitet.Arbeid(dato, 3))

    fun nyRapporteringsperiodeHendelse(
        fom: LocalDate = LocalDate.now().minusDays(14),
    ) = NyRapporteringssyklusHendelse(UUID.randomUUID(), testIdent, fom) { _, tom -> tom }

    fun nyVedtakInnvilgetHendelse(virkningsdato: LocalDate = LocalDate.now()) = VedtakInnvilgetHendelse(
        meldingsreferanseId = UUID.randomUUID(),
        ident = testIdent,
        virkningsdato = virkningsdato,
        opprettet = LocalDateTime.now(),
        UUID.randomUUID(),
    ) { _, tom -> tom }

    fun nyRapporteringspliktDatoHendelse(søknadInnsendtDato: LocalDate = LocalDate.now()) = RapporteringspliktDatoHendelse(
        meldingsreferanseId = UUID.randomUUID(),
        ident = testIdent,
        opprettet = LocalDateTime.now(),
        søknadInnsendtDato = søknadInnsendtDato,
    ) { _, tom -> tom }

    fun godkjennPeriodeHendelse(rapporteringId: UUID = UUID.randomUUID()) =
        GodkjennPeriodeHendelse(testIdent, rapporteringId)
}
