package no.nav.dagpenger.rapportering.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Arbeid
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.FerieEllerFravaer
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Syk
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Utdanning
import no.nav.dagpenger.rapportering.utils.januar
import org.junit.jupiter.api.Test
import java.util.UUID

class DagTest {
    @Test
    fun `kan opprette dag uten aktiviteter`() {
        val dag =
            Dag(
                dato = 1.januar,
                aktiviteter = emptyList(),
                dagIndex = 0,
            )

        with(dag) {
            aktiviteter.size shouldBe 0
        }
    }

    @Test
    fun `kan opprette dag med arbeid og utdanning-aktiviteter`() {
        val dag =
            Dag(
                dato = 1.januar,
                aktiviteter =
                    listOf(
                        Aktivitet(uuid = UUID.randomUUID(), type = Utdanning, timer = null),
                        Aktivitet(uuid = UUID.randomUUID(), type = Arbeid, timer = "PT30M"),
                    ),
                dagIndex = 0,
            )

        with(dag) {
            aktiviteter.size shouldBe 2
            aktiviteter.first().type shouldBe Utdanning
            aktiviteter.last().type shouldBe Arbeid
        }
    }

    @Test
    fun `kan opprette dager med enkelt-aktiviteter`() {
        val sykDag = Dag(dato = 1.januar, aktiviteter = listOf(Aktivitet(uuid = UUID.randomUUID(), type = Syk, timer = null)), dagIndex = 0)
        val ferieDag =
            Dag(
                dato = 2.januar,
                aktiviteter = listOf(Aktivitet(uuid = UUID.randomUUID(), type = FerieEllerFravaer, timer = null)),
                dagIndex = 1,
            )
        val utdanningDag =
            Dag(dato = 3.januar, aktiviteter = listOf(Aktivitet(uuid = UUID.randomUUID(), type = Utdanning, timer = null)), dagIndex = 0)
        val arbeidDag =
            Dag(dato = 1.januar, aktiviteter = listOf(Aktivitet(uuid = UUID.randomUUID(), type = Arbeid, timer = "PT24H")), dagIndex = 0)

        with(sykDag) {
            aktiviteter.size shouldBe 1
            aktiviteter.first().type shouldBe Syk
        }
        with(ferieDag) {
            aktiviteter.size shouldBe 1
            aktiviteter.first().type shouldBe FerieEllerFravaer
        }
        with(utdanningDag) {
            aktiviteter.size shouldBe 1
            aktiviteter.first().type shouldBe Utdanning
        }
        with(arbeidDag) {
            aktiviteter.size shouldBe 1
            aktiviteter.first().type shouldBe Arbeid
        }
    }

    @Test
    fun `kan ikke opprette dag med utdanning og syk-aktiviteter`() {
        shouldThrow<IllegalArgumentException> {
            Dag(
                dato = 1.januar,
                aktiviteter =
                    listOf(
                        Aktivitet(uuid = UUID.randomUUID(), type = Utdanning, timer = null),
                        Aktivitet(uuid = UUID.randomUUID(), type = Syk, timer = null),
                    ),
                dagIndex = 0,
            )
        }
    }

    @Test
    fun `kan ikke opprette dag med duplikate aktivitetstyper`() {
        shouldThrow<IllegalArgumentException> {
            Dag(
                dato = 1.januar,
                aktiviteter =
                    listOf(
                        Aktivitet(uuid = UUID.randomUUID(), type = Utdanning, timer = null),
                        Aktivitet(uuid = UUID.randomUUID(), type = Arbeid, timer = "PT5H30M"),
                        Aktivitet(uuid = UUID.randomUUID(), type = Utdanning, timer = null),
                    ),
                dagIndex = 0,
            )
        }
    }

    @Test
    fun `kan ikke opprette dag med arbeid hvor timer er null`() {
        shouldThrow<IllegalArgumentException> {
            Dag(
                dato = 1.januar,
                aktiviteter = listOf(Aktivitet(uuid = UUID.randomUUID(), type = Arbeid, timer = null)),
                dagIndex = 0,
            )
        }
    }

    @Test
    fun `kan ikke opprette dag med arbeid hvor timer er 0`() {
        shouldThrow<IllegalArgumentException> {
            Dag(
                dato = 1.januar,
                aktiviteter = listOf(Aktivitet(uuid = UUID.randomUUID(), type = Arbeid, timer = "PT0H")),
                dagIndex = 0,
            )
        }
    }

    @Test
    fun `kan ikke opprette dag med arbeid hvor timer er mer enn 24 timer`() {
        shouldThrow<IllegalArgumentException> {
            Dag(
                dato = 1.januar,
                aktiviteter = listOf(Aktivitet(uuid = UUID.randomUUID(), type = Arbeid, timer = "PT25H")),
                dagIndex = 0,
            )
        }
    }

    @Test
    fun `kan ikke opprette dag med arbeid hvor timer ikke er hele eller halve`() {
        shouldThrow<IllegalArgumentException> {
            Dag(
                dato = 1.januar,
                aktiviteter = listOf(Aktivitet(uuid = UUID.randomUUID(), type = Arbeid, timer = "PT23H31M")),
                dagIndex = 0,
            )
        }
    }

    @Test
    fun `kan ikke opprette dag hvor aktivitet som ikke er arbeid har timer`() {
        shouldThrow<IllegalArgumentException> {
            Dag(
                dato = 1.januar,
                aktiviteter = listOf(Aktivitet(uuid = UUID.randomUUID(), type = Syk, timer = "PT1H")),
                dagIndex = 0,
            )
        }
    }
}
