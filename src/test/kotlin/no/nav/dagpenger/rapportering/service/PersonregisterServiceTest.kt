package no.nav.dagpenger.rapportering.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.api.ApiTestSetup.Companion.setEnvConfig
import no.nav.dagpenger.rapportering.config.Configuration.unleash
import no.nav.dagpenger.rapportering.connector.PersonregisterConnector
import no.nav.dagpenger.rapportering.connector.Personstatus
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PersonregisterServiceTest {

    private val ident = "12345678910"
    private val token = "jwtToken"

    private val rapporteringService = mockk<RapporteringService>()
    private val personregisterConnector = mockk<PersonregisterConnector>()

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            setEnvConfig()

            mockkObject(unleash)
        }
    }

    @Test
    fun `Skal oppdatere personstatus hvis Unleash returnerer true, Personregister returnerer IKKE_DAGPENGERBRUKER og har DP`() {
        every { unleash.isEnabled(eq("send-dp-til-personregister")) } returns true
        coEvery { personregisterConnector.hentPersonstatus(eq(ident), eq(token)) } returns Personstatus.IKKE_DAGPENGERBRUKER
        coEvery { personregisterConnector.oppdaterPersonstatus(eq(ident), eq(token), eq(LocalDate.now())) } just runs
        coEvery { rapporteringService.harMeldeplikt(eq(ident), eq(token)) } returns "true"

        val personregisterService = PersonregisterService(personregisterConnector, rapporteringService)

        runBlocking { personregisterService.oppdaterPersonstatus(ident, token) }

        coVerify(exactly = 1) { personregisterConnector.oppdaterPersonstatus(eq(ident), eq(token), eq(LocalDate.now())) }
    }

    @Test
    fun `Skal ikke oppdatere personstatus hvis Unleash returnerer true, Personregister returnerer IKKE_DAGPENGERBRUKER og ikke har DP`() {
        every { unleash.isEnabled(eq("send-dp-til-personregister")) } returns true
        coEvery { personregisterConnector.hentPersonstatus(eq(ident), eq(token)) } returns Personstatus.IKKE_DAGPENGERBRUKER
        coEvery { rapporteringService.harMeldeplikt(eq(ident), eq(token)) } returns "false"

        val personregisterService = PersonregisterService(personregisterConnector, rapporteringService)

        runBlocking { personregisterService.oppdaterPersonstatus(ident, token) }

        coVerify(exactly = 0) { personregisterConnector.oppdaterPersonstatus(any(), any(), any()) }
    }

    @Test
    fun `Skal ikke oppdatere personstatus hvis Unleash returnerer true og Personregister returnerer DAGPENGERBRUKER`() {
        every { unleash.isEnabled(eq("send-dp-til-personregister")) } returns true
        coEvery { rapporteringService.harMeldeplikt(eq(ident), eq(token)) } returns "true"
        coEvery { personregisterConnector.hentPersonstatus(eq(ident), eq(token)) } returns Personstatus.DAGPENGERBRUKER

        val personregisterService = PersonregisterService(personregisterConnector, rapporteringService)

        runBlocking { personregisterService.oppdaterPersonstatus(ident, token) }

        coVerify(exactly = 0) { personregisterConnector.oppdaterPersonstatus(any(), any(), any()) }
    }

    @Test
    fun `Skal ikke oppdatere personstatus hvis Unleash returnerer false`() {
        every { unleash.isEnabled(eq("send-dp-til-personregister")) } returns false

        val personregisterService = PersonregisterService(personregisterConnector, rapporteringService)

        runBlocking { personregisterService.oppdaterPersonstatus(ident, token) }

        coVerify(exactly = 0) { personregisterConnector.oppdaterPersonstatus(any(), any(), any()) }
    }
}
