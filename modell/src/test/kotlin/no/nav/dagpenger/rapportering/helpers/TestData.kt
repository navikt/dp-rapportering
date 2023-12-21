package no.nav.dagpenger.rapportering.helpers

import no.nav.dagpenger.rapportering.Godkjenningslogg
import no.nav.dagpenger.rapportering.Person
import no.nav.dagpenger.rapportering.Rapporteringsperiode
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringssyklusHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringspliktDatoHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.hendelser.VedtakInnvilgetHendelse
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Aktivitetstidslinje
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal object TestData {
    val testIdent = "01010125255"
    val testPerson get() = Person(testIdent)

    fun søknadInnsendtHendelse(localDateTime: LocalDateTime = LocalDateTime.now()) =
        SøknadInnsendtHendelse(UUID.randomUUID(), testIdent, localDateTime, UUID.randomUUID())

    fun nyAktivitetHendelse(
        rapporteringsperiodeId: UUID,
        aktivitet: Aktivitet,
    ) = NyAktivitetHendelse(testIdent, rapporteringsperiodeId, aktivitet)

    fun nyAktivitetHendelse(
        rapporteringsperiodeId: UUID,
        dato: LocalDate,
    ) = nyAktivitetHendelse(rapporteringsperiodeId, Aktivitet.Arbeid(dato, 3))

    fun nyRapporteringsperiodeHendelse(fom: LocalDate = LocalDate.now().minusDays(14)) =
        NyRapporteringssyklusHendelse(UUID.randomUUID(), testIdent, fom) { _, tom -> tom }

    fun lagRapporteringsperiode(
        fom: LocalDate,
        tom: LocalDate,
        tilstand: Rapporteringsperiode.TilstandType,
        id: UUID = UUID.randomUUID(),
        aktiviteter: List<Aktivitet> = emptyList(),
    ): Rapporteringsperiode {
        return Rapporteringsperiode.rehydrer(
            rapporteringsperiodeId = id,
            beregnesEtter = tom,
            fraOgMed = fom,
            tilOgMed = tom,
            tilstand = tilstand,
            opprettet = LocalDateTime.now(),
            tidslinje =
                Aktivitetstidslinje(fom..tom).also {
                    aktiviteter.forEach(it::leggTilAktivitet)
                },
            Godkjenningslogg(),
            korrigerer = null,
        )
    }

    fun nyVedtakInnvilgetHendelse(virkningsdato: LocalDate = LocalDate.now()) =
        VedtakInnvilgetHendelse(
            meldingsreferanseId = UUID.randomUUID(),
            ident = testIdent,
            virkningsdato = virkningsdato,
            opprettet = LocalDateTime.now(),
            UUID.randomUUID(),
        ) { _, tom -> tom }

    fun nyRapporteringspliktDatoHendelse(søknadInnsendtDato: LocalDate = LocalDate.now()) =
        RapporteringspliktDatoHendelse(
            meldingsreferanseId = UUID.randomUUID(),
            ident = testIdent,
            opprettet = LocalDateTime.now(),
            søknadInnsendtDato = søknadInnsendtDato,
        ) { _, tom -> tom }

    fun godkjennPeriodeHendelse(
        rapporteringId: UUID = UUID.randomUUID(),
        dato: LocalDate = LocalDate.now(),
    ) = GodkjennPeriodeHendelse(testIdent, rapporteringId, dato = dato)
}
