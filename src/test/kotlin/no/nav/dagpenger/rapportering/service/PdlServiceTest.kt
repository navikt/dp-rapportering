package no.nav.dagpenger.rapportering.service

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.pdl.PDLPerson
import no.nav.dagpenger.pdl.PersonOppslag
import org.junit.jupiter.api.Test

class PdlServiceTest {
    private val testTokenProvider: () -> String? = { "testToken" }
    private val ident = "12345678903"

    @Test
    fun `kan hente navn uten mellomnavn`() {
        val person = mockk<PDLPerson>()
        every { person.fornavn } returns "Test"
        every { person.mellomnavn } returns null
        every { person.etternavn } returns "Testesen"

        val personOppslag = mockk<PersonOppslag>()
        coEvery { personOppslag.hentPerson(eq(ident), any()) } returns person

        val pdlService =
            PdlService(
                personOppslag = personOppslag,
                tokenProvider = testTokenProvider,
            )

        val response =
            runBlocking {
                pdlService.hentNavn(ident)
            }

        response shouldBe "Test Testesen"
    }

    @Test
    fun `kan hente navn med mellomnavn`() {
        val person = mockk<PDLPerson>()
        every { person.fornavn } returns "Test"
        every { person.mellomnavn } returns "Tests"
        every { person.etternavn } returns "Testesen"

        val personOppslag = mockk<PersonOppslag>()
        coEvery { personOppslag.hentPerson(eq(ident), any()) } returns person

        val pdlService =
            PdlService(
                personOppslag = personOppslag,
                tokenProvider = testTokenProvider,
            )

        val response =
            runBlocking {
                pdlService.hentNavn(ident)
            }

        response shouldBe "Test Tests Testesen"
    }

    @Test
    fun `returnerer tom String ved feil`() {
        val personOppslag = mockk<PersonOppslag>()
        coEvery { personOppslag.hentPerson(eq(ident), any()) } throws Exception("Test exception")

        val pdlService =
            PdlService(
                personOppslag = personOppslag,
                tokenProvider = testTokenProvider,
            )

        val response =
            runBlocking {
                pdlService.hentNavn(ident)
            }

        response shouldBe ""
    }
}
