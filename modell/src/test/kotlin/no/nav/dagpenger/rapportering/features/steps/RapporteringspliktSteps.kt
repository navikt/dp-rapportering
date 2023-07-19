package no.nav.dagpenger.rapportering.features.steps

import io.cucumber.java8.No
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.Person
import no.nav.dagpenger.rapportering.PersonVisitor
import no.nav.dagpenger.rapportering.Rapporteringsplikt
import no.nav.dagpenger.rapportering.RapporteringspliktType
import no.nav.dagpenger.rapportering.helpers.TestData.nyRapporteringspliktDatoHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.nyVedtakInnvilgetHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.søknadInnsendtHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.testPerson
import java.time.LocalDate
import java.util.UUID

class RapporteringspliktSteps : No {
    private lateinit var person: Person

    init {
        Gitt("en ny bruker") {
            person = testPerson
        }

        Når("brukeren søker om dagpenger den {string} og ønsker dagpenger fra {string}") { søknadsdatoStr: String, ønsketdatoStr: String ->
            val søknadsdato = LocalDate.parse(søknadsdatoStr)
            val ønsketdato = LocalDate.parse(ønsketdatoStr)
            person.behandle(søknadInnsendtHendelse())
            person.behandle(nyRapporteringspliktDatoHendelse(søknadsdato, ønsketdato))
        }

        Når("brukeren får innvilget vedtak om dagpenger med virkningsdato {string}") { dato: String ->
            val virkningsdato = LocalDate.parse(dato)
            person.behandle(nyVedtakInnvilgetHendelse(virkningsdato))
        }

        Så("skal brukeren få rapporteringsplikt på grunn av {string}") { arsak: String ->
            val type = when (arsak) {
                "søknad" -> RapporteringspliktType.Søknad
                "vedtak" -> RapporteringspliktType.Vedtak
                else -> throw IllegalArgumentException("Ukjent årsak til rapporteringsplikt")
            }
            person.rapporteringsplikt.type shouldBe type
        }

        Så("rapporteringsplikten gjelder fra {string}") { gjelderFraStr: String ->
            val gjelderFra = LocalDate.parse(gjelderFraStr).atStartOfDay()
            person.rapporteringsplikt.rapporteringspliktFra shouldBe gjelderFra
        }
    }

    private val Person.rapporteringsplikt get() = TestRapporteringspliktVisitor(this).rapporteringsplikt

    private class TestRapporteringspliktVisitor(person: Person) : PersonVisitor {
        lateinit var rapporteringsplikt: Rapporteringsplikt

        init {
            person.accept(this)
        }

        override fun visit(rapporteringsplikt: Rapporteringsplikt, id: UUID, type: RapporteringspliktType) {
            this.rapporteringsplikt = rapporteringsplikt
        }
    }
}
