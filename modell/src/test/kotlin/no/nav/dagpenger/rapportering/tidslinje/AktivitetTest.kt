package no.nav.dagpenger.rapportering.tidslinje

import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import no.nav.dagpenger.rapportering.helpers.TestData.nyRapporteringHendelse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.UUID
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class AktivitetTest {
    @Test
    fun `aktivitet har tilstand`() {
        val aktivitet = Aktivitet.Arbeid(LocalDate.now(), 3)

        aktivitet.håndter(nyRapporteringHendelse())

        assertThrows<IllegalStateException> {
            aktivitet.håndter(nyRapporteringHendelse())
        }
    }

    @Test
    fun `rehydrering funksjoner`() {
        val uuid = UUID.randomUUID()
        val dato = LocalDate.now()

        Aktivitet.rehydrer(uuid, dato, "Arbeid", 7.5, "Ny").let {
            it should beInstanceOf<Aktivitet.Arbeid>()
            it.dato shouldBe dato
            it.uuid shouldBe uuid
            it.tid shouldBe 7.5.toDuration(DurationUnit.HOURS)
        }

        Aktivitet.rehydrer(
            uuid = uuid,
            dato = dato,
            type = "Syk",
            tilstand = "Ny",
        ).let {
            it should beInstanceOf<Aktivitet.Syk>()
            it.dato shouldBe dato
            it.uuid shouldBe uuid
        }
        Aktivitet.rehydrer(
            uuid = uuid,
            dato = dato,
            type = "Ferie",
            tilstand = "Ny",
        ).let {
            it should beInstanceOf<Aktivitet.Ferie>()
            it.dato shouldBe dato
            it.uuid shouldBe uuid
        }
    }
}
