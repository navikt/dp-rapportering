package no.nav.dagpenger.rapportering

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class MediatorTest {
    private val rapid = TestRapid()
    private val mediator
        get() = Mediator(rapid)
    private val testIdent = "12312312311"
    private val soknadInnsendtHendelse: SoknadInnsendtHendelse
        get() = SoknadInnsendtHendelse(UUID.randomUUID(), testIdent, LocalDateTime.now(), UUID.randomUUID())

    @Test
    fun mediatorflyt() {
        mediator.behandle(soknadInnsendtHendelse)
    }
}
