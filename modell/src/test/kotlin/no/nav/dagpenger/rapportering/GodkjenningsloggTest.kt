package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.Godkjenning.Saksbehandler
import no.nav.dagpenger.rapportering.Godkjenning.Sluttbruker
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GodkjenningsloggTest {
    @Test
    fun foo() {
        val godkjenninger = Godkjenningslogg()
        godkjenninger.godkjent() shouldBe false

        godkjenninger.leggTil(Godkjenning(Sluttbruker("123")))
        godkjenninger.godkjent() shouldBe true

        assertThrows<IllegalArgumentException> {
            godkjenninger.leggTil(Godkjenning(Saksbehandler("123"), "foo"))
        }

        godkjenninger.avgodkjenn()
        godkjenninger.godkjent() shouldBe false

        assertThrows<IllegalArgumentException> {
            godkjenninger.avgodkjenn()
        }
    }
}
