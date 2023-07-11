package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.Godkjenningsendring.Saksbehandler
import no.nav.dagpenger.rapportering.Godkjenningsendring.Sluttbruker
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GodkjenningsloggTest {
    @Test
    fun foo() {
        val godkjenninger = Godkjenningslogg()
        godkjenninger.godkjent() shouldBe false

        godkjenninger.leggTil(Godkjenningsendring(Sluttbruker("123")))
        godkjenninger.godkjent() shouldBe true

        assertThrows<IllegalArgumentException> {
            godkjenninger.leggTil(Godkjenningsendring(Saksbehandler("123"), "foo"))
        }

        godkjenninger.avgodkjenn(Godkjenningsendring(Saksbehandler("123"), "bar"))
        godkjenninger.godkjent() shouldBe false

        assertThrows<IllegalArgumentException> {
            godkjenninger.avgodkjenn(Godkjenningsendring(Saksbehandler("123"), "foobar"))
        }
    }
}
