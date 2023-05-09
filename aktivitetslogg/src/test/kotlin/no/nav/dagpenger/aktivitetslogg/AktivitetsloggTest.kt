package no.nav.dagpenger.aktivitetslogg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AktivitetsloggTest {
    private lateinit var aktivitetslogg: Aktivitetslogg

    @BeforeEach
    fun setUp() {
        aktivitetslogg = Aktivitetslogg()
    }

    @Test
    fun `info should add an Aktivitet Info to the log`() {
        aktivitetslogg.info("This is an info message")
        assertEquals(1, aktivitetslogg.aktivitetsteller())
        assertTrue(aktivitetslogg.behov().isEmpty())
    }

    /*
    @Test
    fun `behov should add an Aktivitet Behov to the log`() {
        val detaljer = mapOf("key" to "value")
        aktivitetslogg.behov(Behov.Behovtype.ARBEIDSSØKER, "This is a behov message", detaljer)
        assertEquals(1, aktivitetslogg.aktivitetsteller())
    }

    @Test
    fun `logg should create a new Aktivitetslogg with filtered aktiviteter`() {
        aktivitetslogg.info("Info message", "param1", "param2")
        aktivitetslogg.behov(Behov.Behovtype.ARBEIDSSØKER, "Behov message", mapOf("key" to "value"))
        aktivitetslogg.kontekst(Aktivitetskontekst("test"))
        val newLog = aktivitetslogg.logg(Aktivitetskontekst("test"), Aktivitetskontekst("other"))
        assertEquals(1, newLog.aktivitetsteller())
        assertEquals("Info message", newLog.info().first().melding)
    }*/
}
