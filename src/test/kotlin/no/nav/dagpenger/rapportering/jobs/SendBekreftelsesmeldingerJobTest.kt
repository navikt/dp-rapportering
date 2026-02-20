package no.nav.dagpenger.rapportering.jobs

import no.nav.dagpenger.rapportering.api.ApiTestSetup.Companion.setEnvConfig
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class SendBekreftelsesmeldingerJobTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            setEnvConfig()
        }
    }

    @Test
    fun `skal utføre oppgaver`() {
        // TODO:
    }
}
