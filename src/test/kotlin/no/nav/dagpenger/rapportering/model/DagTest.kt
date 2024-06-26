package no.nav.dagpenger.rapportering.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.connector.AdapterAktivitet
import no.nav.dagpenger.rapportering.connector.AdapterAktivitet.AdapterAktivitetsType
import no.nav.dagpenger.rapportering.connector.AdapterDag
import no.nav.dagpenger.rapportering.connector.toAdapterDag
import no.nav.dagpenger.rapportering.connector.toDag
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Arbeid
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Fravaer
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
                        Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null),
                        Aktivitet(id = UUID.randomUUID(), type = Arbeid, timer = "PT30M"),
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
        val sykDag = Dag(dato = 1.januar, aktiviteter = listOf(Aktivitet(id = UUID.randomUUID(), type = Syk, timer = null)), dagIndex = 0)
        val ferieDag =
            Dag(
                dato = 2.januar,
                aktiviteter = listOf(Aktivitet(id = UUID.randomUUID(), type = Fravaer, timer = null)),
                dagIndex = 1,
            )
        val utdanningDag =
            Dag(dato = 3.januar, aktiviteter = listOf(Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null)), dagIndex = 0)
        val arbeidDag =
            Dag(dato = 1.januar, aktiviteter = listOf(Aktivitet(id = UUID.randomUUID(), type = Arbeid, timer = "PT24H")), dagIndex = 0)

        with(sykDag) {
            aktiviteter.size shouldBe 1
            aktiviteter.first().type shouldBe Syk
        }
        with(ferieDag) {
            aktiviteter.size shouldBe 1
            aktiviteter.first().type shouldBe Fravaer
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
                        Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null),
                        Aktivitet(id = UUID.randomUUID(), type = Syk, timer = null),
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
                        Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null),
                        Aktivitet(id = UUID.randomUUID(), type = Arbeid, timer = "PT5H30M"),
                        Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null),
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
                aktiviteter = listOf(Aktivitet(id = UUID.randomUUID(), type = Arbeid, timer = null)),
                dagIndex = 0,
            )
        }
    }

    @Test
    fun `kan ikke opprette dag med arbeid hvor timer er 0`() {
        shouldThrow<IllegalArgumentException> {
            Dag(
                dato = 1.januar,
                aktiviteter = listOf(Aktivitet(id = UUID.randomUUID(), type = Arbeid, timer = "PT0H")),
                dagIndex = 0,
            )
        }
    }

    @Test
    fun `kan ikke opprette dag med arbeid hvor timer er mer enn 24 timer`() {
        shouldThrow<IllegalArgumentException> {
            Dag(
                dato = 1.januar,
                aktiviteter = listOf(Aktivitet(id = UUID.randomUUID(), type = Arbeid, timer = "PT25H")),
                dagIndex = 0,
            )
        }
    }

    @Test
    fun `kan ikke opprette dag med arbeid hvor timer ikke er hele eller halve`() {
        shouldThrow<IllegalArgumentException> {
            Dag(
                dato = 1.januar,
                aktiviteter = listOf(Aktivitet(id = UUID.randomUUID(), type = Arbeid, timer = "PT23H31M")),
                dagIndex = 0,
            )
        }
    }

    @Test
    fun `kan ikke opprette dag hvor aktivitet som ikke er arbeid har timer`() {
        shouldThrow<IllegalArgumentException> {
            Dag(
                dato = 1.januar,
                aktiviteter = listOf(Aktivitet(id = UUID.randomUUID(), type = Syk, timer = "PT1H")),
                dagIndex = 0,
            )
        }
    }

    @Test
    fun `kan konvertere Dag til AdapterDag`() {
        val aktivitet = Aktivitet(id = UUID.randomUUID(), type = Syk, timer = null)
        val dag = Dag(dato = 1.januar, aktiviteter = listOf(aktivitet), dagIndex = 0)

        val adapterDag = dag.toAdapterDag()

        adapterDag.dato shouldBe 1.januar
        adapterDag.dagIndex shouldBe 0
        adapterDag.aktiviteter.first().uuid shouldBe aktivitet.id
        adapterDag.aktiviteter.first().type shouldBe AdapterAktivitetsType.Syk
        adapterDag.aktiviteter.first().timer shouldBe null
    }

    @Test
    fun `ved konvertering fra Dag til AdapterDag konverteres timer riktig`() {
        val aktivitet = Aktivitet(id = UUID.randomUUID(), type = Arbeid, timer = "PT5H30M")
        val dag = Dag(dato = 1.januar, aktiviteter = listOf(aktivitet), dagIndex = 0)

        val adapterDag = dag.toAdapterDag()

        adapterDag.dato shouldBe 1.januar
        adapterDag.dagIndex shouldBe 0
        adapterDag.aktiviteter.first().uuid shouldBe aktivitet.id
        adapterDag.aktiviteter.first().type shouldBe AdapterAktivitetsType.Arbeid
        adapterDag.aktiviteter.first().timer shouldBe 5.5
    }

    @Test
    fun `kan konvertere AdapterDag til Dag`() {
        val adapterAktivitet = AdapterAktivitet(uuid = UUID.randomUUID(), type = AdapterAktivitetsType.Syk, timer = null)
        val adapterDag = AdapterDag(dato = 1.januar, aktiviteter = listOf(adapterAktivitet), dagIndex = 0)

        val dag = adapterDag.toDag()

        dag.dato shouldBe 1.januar
        dag.dagIndex shouldBe 0
        dag.aktiviteter.first().id shouldBe adapterAktivitet.uuid
        dag.aktiviteter.first().type shouldBe Syk
        dag.aktiviteter.first().timer shouldBe null
    }

    @Test
    fun `ved konvertering fra AdapterDag til Dag konverteres timer riktig`() {
        val adapterAktivitet = AdapterAktivitet(uuid = UUID.randomUUID(), type = AdapterAktivitetsType.Arbeid, timer = 5.5)
        val adapterDag = AdapterDag(dato = 1.januar, aktiviteter = listOf(adapterAktivitet), dagIndex = 0)

        val dag = adapterDag.toDag()

        dag.dato shouldBe 1.januar
        dag.dagIndex shouldBe 0
        dag.aktiviteter.first().id shouldBe adapterAktivitet.uuid
        dag.aktiviteter.first().type shouldBe Arbeid
        dag.aktiviteter.first().timer shouldBe "PT5H30M"
    }

    @Test
    fun `ved konvertering fra AdapterDag til Dag slår validering inn`() {
        val adapterDag = AdapterDag(dato = 1.januar, aktiviteter = listOf(), dagIndex = 0)

        // Timer må være null hvis ikke type er Arbeid
        shouldThrow<IllegalArgumentException> {
            adapterDag
                .copy(
                    aktiviteter =
                        listOf(
                            AdapterAktivitet(uuid = UUID.randomUUID(), type = AdapterAktivitetsType.Utdanning, timer = 0.5),
                        ),
                ).toDag()
        }

        // Ingen duplikate aktivitetstyper på samme dag
        shouldThrow<IllegalArgumentException> {
            adapterDag
                .copy(
                    aktiviteter =
                        listOf(
                            AdapterAktivitet(uuid = UUID.randomUUID(), type = AdapterAktivitetsType.Utdanning, timer = null),
                            AdapterAktivitet(uuid = UUID.randomUUID(), type = AdapterAktivitetsType.Utdanning, timer = null),
                        ),
                ).toDag()
        }

        // Typer som ikke er tillatt sammen på samme dag
        shouldThrow<IllegalArgumentException> {
            adapterDag
                .copy(
                    aktiviteter =
                        listOf(
                            AdapterAktivitet(uuid = UUID.randomUUID(), type = AdapterAktivitetsType.Arbeid, timer = 0.5),
                            AdapterAktivitet(uuid = UUID.randomUUID(), type = AdapterAktivitetsType.Syk, timer = null),
                        ),
                ).toDag()
        }

        // Timer må være hele eller halve
        shouldThrow<IllegalArgumentException> {
            adapterDag
                .copy(
                    aktiviteter =
                        listOf(
                            AdapterAktivitet(uuid = UUID.randomUUID(), type = AdapterAktivitetsType.Arbeid, timer = 0.2),
                        ),
                ).toDag()
        }
    }
}
