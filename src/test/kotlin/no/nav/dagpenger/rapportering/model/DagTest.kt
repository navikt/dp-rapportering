package no.nav.dagpenger.rapportering.model

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.connector.AdapterAktivitet
import no.nav.dagpenger.rapportering.connector.AdapterAktivitet.AdapterAktivitetsType
import no.nav.dagpenger.rapportering.connector.AdapterDag
import no.nav.dagpenger.rapportering.connector.toAdapterDag
import no.nav.dagpenger.rapportering.connector.toDag
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Arbeid
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Syk
import no.nav.dagpenger.rapportering.utils.januar
import org.junit.jupiter.api.Test
import java.util.UUID

class DagTest {
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
}
