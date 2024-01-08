package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.Godkjenningsendring.Saksbehandler
import no.nav.dagpenger.rapportering.Godkjenningsendring.Sluttbruker
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GodkjenningsloggTest {
    @Test
    fun `skal ikke være godkjent initialt`() {
        val godkjenninger = Godkjenningslogg()

        godkjenninger.godkjent() shouldBe false
    }

    @Test
    fun `skal være godkjent etter å ha lagt til Godkjenningsendring`() {
        val godkjenninger = Godkjenningslogg()

        godkjenninger.leggTil(Godkjenningsendring(Sluttbruker("123")))

        godkjenninger.godkjent() shouldBe true
    }

    @Test
    fun `kan ikke avgodkjenne uten å være godkjent`() {
        val godkjenninger = Godkjenningslogg()

        assertThrows<IllegalArgumentException> {
            godkjenninger.avgodkjenn(Godkjenningsendring(Saksbehandler("123"), ""))
        }
    }

    @Test
    fun `kan avgodkjenne etter å ha blitt godkjent`() {
        val godkjenninger = Godkjenningslogg()

        with(godkjenninger) {
            leggTil(Godkjenningsendring(Sluttbruker("123")))
            avgodkjenn(Godkjenningsendring(Saksbehandler("123"), ""))
        }

        godkjenninger.godkjent() shouldBe false
    }

    @Test
    fun `kan bare endre dersom det er samme kilde`() {
        val saksbehandler = Saksbehandler("saksbehandler1")
        val sluttbruker = Sluttbruker("bruker1")
        val endring = Godkjenningsendring(sluttbruker)

        with(endring) {
            kanEndre(saksbehandler) shouldBe false
            kanEndre(sluttbruker) shouldBe true
        }
    }
}
