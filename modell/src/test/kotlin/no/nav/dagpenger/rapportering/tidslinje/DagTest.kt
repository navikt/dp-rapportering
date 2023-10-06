package no.nav.dagpenger.rapportering.tidslinje

import no.nav.dagpenger.rapportering.tidslinje.Dag.Companion.eldsteDagFørst
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class DagTest {
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

        assertEquals(setOf(dag1, dag2, dag3), sortert)
    }
}
