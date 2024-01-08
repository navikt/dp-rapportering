package no.nav.dagpenger.rapportering.tidslinje

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.dagpenger.rapportering.DagVisitor
import no.nav.dagpenger.rapportering.helpers.desember
import no.nav.dagpenger.rapportering.helpers.januar
import no.nav.dagpenger.rapportering.helpers.mai
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.AktivitetType
import no.nav.dagpenger.rapportering.tidslinje.Dag.Companion.eldsteDagFørst
import no.nav.dagpenger.rapportering.tidslinje.Dag.StrategiType
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DagTest {
    @Test
    fun `dag har Arbeid, Syk og Ferie som mulige aktiviteter`() {
        val dag = Dag(LocalDate.now())
        val dagVisitor = DagVisitorTest()

        dag.accept(dagVisitor)

        with(dagVisitor) {
            muligeAktiviteter shouldNotBe emptyList<Aktivitet>()
            muligeAktiviteter shouldBe listOf(AktivitetType.Arbeid, AktivitetType.Syk, AktivitetType.Ferie)
        }
    }

    @Test
    fun `dag har ikke mulige aktiviteter`() {
        val dag = Dag(dato = LocalDate.now(), aktiviteter = mutableListOf(), strategiType = StrategiType.IngentingErMulig)
        val dagVisitor = DagVisitorTest()

        dag.accept(dagVisitor)

        dagVisitor.muligeAktiviteter shouldBe emptyList()
    }

    @Test
    fun `helligdag har ingen aktivitet`() {
        val dag = Dag(17.mai)
        val dagVisitor = DagVisitorTest()

        dag.accept(dagVisitor)

        dag.harAktivitet() shouldBe false
        dagVisitor.muligeAktiviteter shouldBe emptyList()
    }

    @Test
    fun `dager sammenfaller`() {
        val dag1 = Dag(LocalDate.now())
        val dag2 = Dag(LocalDate.now())
        val dag3 = Dag(LocalDate.now().plusDays(1))

        with(dag1) {
            sammenfallerMed(dag2) shouldBe true
            sammenfallerMed(dag3) shouldBe false
        }
    }

    @Test
    fun `dager med samme dato sammenfaller`() {
        val dag1 = Dag(LocalDate.now())
        val dag2 = Dag(LocalDate.now())
        val dag3 = Dag(LocalDate.now().plusDays(1))

        with(dag1) {
            sammenfallerMed(dag2.dato) shouldBe true
            sammenfallerMed(dag3.dato) shouldBe false
        }
    }

    @Test
    fun `dag dekkes av perioden`() {
        val dag = Dag(1.januar)
        val periode = 1.januar..14.januar

        dag.dekkesAv(periode) shouldBe true
    }

    @Test
    fun `dag dekkes ikke av perioden`() {
        val dag = Dag(1.januar)
        val periode = 2.januar..14.januar

        dag.dekkesAv(periode) shouldBe false
    }

    @Test
    fun `kan ikke legge til aktivitet på en helligdag`() {
        val dag = Dag(25.desember)
        val aktivitet = Aktivitet.Arbeid(25.desember, "PT3H20M")

        shouldThrow<IllegalStateException> { dag.leggTilAktivitet(aktivitet) }
    }

    @Test
    fun `kan ikke legge til aktivitet på en fritaksdag`() {
        val dag = Dag(2.januar)
        val aktivitet = Aktivitet.Arbeid(2.januar, "PT3H20M")

        dag.leggTilFritak()

        shouldThrow<IllegalStateException> { dag.leggTilAktivitet(aktivitet) }
    }

    @Test
    fun `Kan ikke legge til aktivitet på en annen dato enn aktivitetensdato`() {
        val dag = Dag(3.januar)
        val aktivitet = Aktivitet.Arbeid(2.januar, "PT3H20M")

        shouldThrow<IllegalStateException> { dag.leggTilAktivitet(aktivitet) }
    }

    @Test
    fun `kan legge til aktivitet`() {
        val dag = Dag(4.januar)
        val dagVisitor = DagVisitorTest()
        val aktivitet = Aktivitet.Arbeid(4.januar, "PT3H20M")

        dag.leggTilAktivitet(aktivitet)
        dag.accept(dagVisitor)

        dag.harAktivitet() shouldBe true
        dagVisitor.aktiviteter shouldBe listOf(aktivitet)
    }

    @Test
    fun `dager sorteres i riktig rekkefølge`() {
        val dag1 = Dag(LocalDate.now().minusDays(1))
        val dag2 = Dag(LocalDate.now())
        val dag3 = Dag(LocalDate.now().plusDays(1))
        val sortert =
            sortedSetOf(
                eldsteDagFørst,
                dag2,
                dag1,
                dag3,
            )

        sortert shouldBe setOf(dag1, dag2, dag3)
    }
}

class DagVisitorTest : DagVisitor {
    val muligeAktiviteter = mutableListOf<AktivitetType>()
    val aktiviteter = mutableListOf<Aktivitet>()

    override fun visit(
        dag: Dag,
        dato: LocalDate,
        aktiviteter: List<Aktivitet>,
        muligeAktiviter: List<AktivitetType>,
        strategi: StrategiType,
    ) {
        this.muligeAktiviteter.addAll(muligeAktiviter)
        this.aktiviteter.addAll(aktiviteter)
    }
}
