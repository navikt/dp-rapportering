package no.nav.dagpenger.rapportering.utils

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.utils.ExtensionFunctionsTest.EnEnum.VALUE_1
import org.junit.jupiter.api.Test

class ExtensionFunctionsTest {
    private enum class EnEnum {
        VALUE_1,
        VALUE_2,
        VALUE_3,
    }

    @Test
    fun `enum entries valueOrNull returnerer forventet enum hvis enum med name eksisterer`() {
        EnEnum.entries.valueOfOrNull("VALUE_1") shouldBe VALUE_1
    }

    @Test
    fun `enum entries valueOrNull returnerer null hvis enum med name ikke eksisterer`() {
        EnEnum.entries.valueOfOrNull("VALUE_4") shouldBe null
    }

    @Test
    fun `enum entries valueOrNull returnerer null hvis input er null`() {
        EnEnum.entries.valueOfOrNull(null) shouldBe null
    }
}
