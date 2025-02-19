package no.nav.dagpenger.rapportering.utils

import io.kotest.assertions.throwables.shouldThrow
import io.ktor.server.plugins.BadRequestException
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Arbeid
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Fravaer
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Syk
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Utdanning
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class InnsendingkontrollTest {
    @Test
    fun `kontroll av innsending kaster ikke feil hvis perioden kan sendes og har registrert arbeidssøker`() {
        val periode = lagRapporteringsperiode()
        kontrollerRapporteringsperiode(periode)
    }

    @Test
    fun `kontroll feiler hvis kanSendes er false`() {
        val periode = lagRapporteringsperiode(kanSendes = false)
        shouldThrow<BadRequestException> {
            kontrollerRapporteringsperiode(periode)
        }
    }

    @Test
    fun `kontroll feiler hvis kanSendesFra ikke har passert`() {
        val periode = lagRapporteringsperiode(kanSendesFra = LocalDate.now().plusDays(1))
        shouldThrow<BadRequestException> {
            kontrollerRapporteringsperiode(periode)
        }
    }

    @Test
    fun `kontroll feiler ikke hvis kanSendesFra har passert`() {
        val periode = lagRapporteringsperiode(kanSendesFra = LocalDate.now().minusDays(1))
        kontrollerRapporteringsperiode(periode)
    }

    @Test
    fun `kontroll feiler ikke hvis kanSendesFra er i dag`() {
        val periode = lagRapporteringsperiode(kanSendesFra = LocalDate.now().minusDays(1))
        kontrollerRapporteringsperiode(periode)
    }

    @Test
    fun `kontroll feiler hvis registrertArbeidssoker er null`() {
        val periode = lagRapporteringsperiode(registrertArbeidssoker = null)
        shouldThrow<BadRequestException> {
            kontrollerRapporteringsperiode(periode)
        }
    }

    @Test
    fun `kontroll feiler ikke når arbeid og utdanning kombineres`() {
        val periode =
            lagRapporteringsperiode(
                dager =
                    getDager(
                        aktiviteter =
                            listOf(
                                Aktivitet(id = UUID.randomUUID(), type = Arbeid, timer = "PT7H30M"),
                                Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null),
                            ),
                    ),
            )
        kontrollerRapporteringsperiode(periode)
    }

    @Test
    fun `kontroll feiler ikke når utdanning og syk kombineres`() {
        val periode =
            lagRapporteringsperiode(
                dager =
                    getDager(
                        aktiviteter =
                            listOf(
                                Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null),
                                Aktivitet(id = UUID.randomUUID(), type = Syk, timer = null),
                            ),
                    ),
            )
        kontrollerRapporteringsperiode(periode)
    }

    @Test
    fun `kontroll feiler ikke når utdanning og fravær kombineres`() {
        val periode =
            lagRapporteringsperiode(
                dager =
                    getDager(
                        aktiviteter =
                            listOf(
                                Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null),
                                Aktivitet(id = UUID.randomUUID(), type = Fravaer, timer = null),
                            ),
                    ),
            )
        kontrollerRapporteringsperiode(periode)
    }

    @Test
    fun `kontroll feiler når det finnes duplikate aktivitetstyper på samme dag`() {
        val periode =
            lagRapporteringsperiode(
                dager =
                    getDager(
                        aktiviteter =
                            listOf(
                                Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null),
                                Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null),
                            ),
                    ),
            )
        shouldThrow<BadRequestException> {
            kontrollerRapporteringsperiode(periode)
        }
    }

    @Test
    fun `kontroll feiler når en dag har mer enn 2 aktivitetstyper`() {
        val periode =
            lagRapporteringsperiode(
                dager =
                    getDager(
                        aktiviteter =
                            listOf(
                                Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null),
                                Aktivitet(id = UUID.randomUUID(), type = Arbeid, timer = "PT5H30M"),
                                Aktivitet(id = UUID.randomUUID(), type = Syk, timer = null),
                            ),
                    ),
            )
        shouldThrow<BadRequestException> {
            kontrollerRapporteringsperiode(periode)
        }
    }

    @Test
    fun `kontroll feiler når arbeid ikke har timer`() {
        val periode =
            lagRapporteringsperiode(
                dager =
                    getDager(
                        aktiviteter =
                            listOf(
                                Aktivitet(id = UUID.randomUUID(), type = Arbeid, timer = null),
                            ),
                    ),
            )
        shouldThrow<BadRequestException> {
            kontrollerRapporteringsperiode(periode)
        }
    }

    @Test
    fun `kontroll feiler når arbeid har 0 timer`() {
        val periode =
            lagRapporteringsperiode(
                dager =
                    getDager(
                        aktiviteter =
                            listOf(
                                Aktivitet(id = UUID.randomUUID(), type = Arbeid, timer = "PT0M"),
                            ),
                    ),
            )
        shouldThrow<BadRequestException> {
            kontrollerRapporteringsperiode(periode)
        }
    }

    @Test
    fun `kontroll feiler når arbeid har mer enn 24 timer`() {
        val periode =
            lagRapporteringsperiode(
                dager =
                    getDager(
                        aktiviteter =
                            listOf(
                                Aktivitet(id = UUID.randomUUID(), type = Arbeid, timer = "PT25H"),
                            ),
                    ),
            )
        shouldThrow<BadRequestException> {
            kontrollerRapporteringsperiode(periode)
        }
    }

    @Test
    fun `kontroll feiler når arbeid har timer som ikke er hele eller halve`() {
        val periode =
            lagRapporteringsperiode(
                dager =
                    getDager(
                        aktiviteter =
                            listOf(
                                Aktivitet(id = UUID.randomUUID(), type = Arbeid, timer = "PT5H15M"),
                            ),
                    ),
            )
        shouldThrow<BadRequestException> {
            kontrollerRapporteringsperiode(periode)
        }
    }

    @Test
    fun `kontroll feiler når aktivitet som ikke er arbeid har timer`() {
        val periode =
            lagRapporteringsperiode(
                dager =
                    getDager(
                        aktiviteter =
                            listOf(
                                Aktivitet(id = UUID.randomUUID(), type = Syk, timer = "PT1H"),
                            ),
                    ),
            )
        shouldThrow<BadRequestException> {
            kontrollerRapporteringsperiode(periode)
        }
    }
}

fun lagRapporteringsperiode(
    kanSendes: Boolean = true,
    kanSendesFra: LocalDate = LocalDate.now().minusDays(1),
    registrertArbeidssoker: Boolean? = true,
    dager: List<Dag> = getDager(startDato = LocalDate.now().minusDays(13)),
) = Rapporteringsperiode(
    id = 1,
    type = "05",
    periode = Periode(LocalDate.now().minusDays(13), LocalDate.now()),
    dager = dager,
    kanSendesFra = kanSendesFra,
    kanSendes = kanSendes,
    kanEndres = false,
    bruttoBelop = null,
    status = TilUtfylling,
    registrertArbeidssoker = registrertArbeidssoker,
    begrunnelseEndring = null,
    originalId = null,
    rapporteringstype = null,
    mottattDato = null,
)

private fun getDager(
    startDato: LocalDate = 1.januar,
    aktiviteter: List<Aktivitet>? = null,
): List<Dag> =
    (0..13)
        .map { i ->
            Dag(
                dato = startDato.plusDays(i.toLong()),
                aktiviteter = aktiviteter ?: emptyList(),
                dagIndex = i,
            )
        }
