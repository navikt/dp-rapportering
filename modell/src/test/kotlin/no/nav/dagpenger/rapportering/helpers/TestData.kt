package no.nav.dagpenger.rapportering.helpers

import no.nav.dagpenger.rapportering.Person
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringHendelse
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringsperiodeHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import java.time.LocalDate
import java.util.UUID

internal object TestData {
    val testIdent = "01010125255"
    val testPerson get() = Person(testIdent)

    fun søknadInnsendtHendelse() = SøknadInnsendtHendelse(
        UUID.randomUUID(),
        testIdent,
    )

    fun nyAktivitetHendelse(vararg aktivitet: Aktivitet) = NyAktivitetHendelse(
        UUID.randomUUID(),
        testIdent,
        aktivitet.toList(),
    )

    fun nyRapporteringsperiodeHendelse(
        fom: LocalDate = LocalDate.now().minusDays(14),
        tom: LocalDate = LocalDate.now(),
    ) = NyRapporteringsperiodeHendelse(
        UUID.randomUUID(),
        testIdent,
        fom,
        tom,
    )

    fun nyRapporteringHendelse(
        rapporteringId: UUID = UUID.randomUUID(),
    ) = NyRapporteringHendelse(
        UUID.randomUUID(),
        testIdent,
        rapporteringId,
    )
}
