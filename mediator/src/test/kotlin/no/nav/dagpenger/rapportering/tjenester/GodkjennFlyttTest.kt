package no.nav.dagpenger.rapportering.tjenester

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.dagpenger.rapportering.BehovMediator
import no.nav.dagpenger.rapportering.Mediator
import no.nav.dagpenger.rapportering.MineBehov.JournalføreRapportering
import no.nav.dagpenger.rapportering.MineBehov.MellomlagreRapportering
import no.nav.dagpenger.rapportering.Person
import no.nav.dagpenger.rapportering.PersonVisitor
import no.nav.dagpenger.rapportering.Rapporteringsperiode
import no.nav.dagpenger.rapportering.db.Postgres
import no.nav.dagpenger.rapportering.db.PostgresDataSourceBuilder
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringspliktDatoHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.hendelser.VedtakInnvilgetHendelse
import no.nav.dagpenger.rapportering.repository.PostgresRepository
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class GodkjennFlyttTest {
    private val rapid = TestRapid()
    private val behovMediator = BehovMediator(rapid)
    private val mediator
        get() =
            Mediator(
                rapid,
                PostgresRepository(PostgresDataSourceBuilder.dataSource),
                behovMediator,
                mockk(relaxed = true),
            )
    private val testIdent = "12312312311"

    @Test
    fun `Går gjennom flytten`() {
        Postgres.withMigratedDb {
            RapporteringMellomlagretMottak(rapid, mediator)
            RapporteringJournalførtMottak(rapid, mediator)

            mediator.behandle(
                SøknadInnsendtHendelse(
                    UUID.randomUUID(),
                    testIdent,
                    LocalDateTime.now(),
                    UUID.randomUUID(),
                ),
            )
            mediator.behandle(
                RapporteringspliktDatoHendelse(
                    UUID.randomUUID(),
                    testIdent,
                    LocalDateTime.now(),
                    LocalDate.now(),
                ) { _, tom -> tom },
            )
            mediator.behandle(
                VedtakInnvilgetHendelse(
                    UUID.randomUUID(),
                    testIdent,
                    LocalDate.now(),
                    LocalDateTime.now(),
                    UUID.randomUUID(),
                ) { _, tom -> tom },
            )

            val person = mediator.hentEllerOpprettPerson(testIdent)
            val rapporteringsperiodeId = person.aktivRapporteringsperiodeId
            val json = "{\"key1\": \"value1\"}"

            val hendelse =
                GodkjennPeriodeHendelse(
                    testIdent,
                    rapporteringsperiodeId,
                    person.aktivRapporteringsperiode.kanGodkjennesFra,
                )
            hendelse.behov(
                MellomlagreRapportering,
                "Trenger å mellomlagre rapportering",
                mapOf(
                    "periodeId" to rapporteringsperiodeId,
                    "json" to json,
                ),
            )
            mediator.behandle(hendelse)

            rapid.inspektør.size shouldBe 3
            var behov = rapid.inspektør.message(2)
            behov.has("@behov") shouldBe true
            behov.get("@behov")[0].asText() shouldBe MellomlagreRapportering.name
            behov.has(MellomlagreRapportering.name) shouldBe true
            behov.get(MellomlagreRapportering.name).get("periodeId").asText() shouldBe rapporteringsperiodeId.toString()
            behov.get(MellomlagreRapportering.name).get("json").asText() shouldBe json

            val løstBehovMellomlagreRapportering =
                """
                {
                    "@event_name": "behov",
                    "@behovId": "${UUID.randomUUID()}",
                    "@behov": [
                        "MellomlagreRapportering",
                        "JournalføreRapportering"
                    ],
                    "meldingsreferanseId": "${UUID.randomUUID()}",
                    "ident": "$testIdent",
                    "MellomlagreRapportering": {
                        "periodeId": "$rapporteringsperiodeId",
                        "json": "${json.replace("\"", "\\\"")}"
                    },
                    "@id": "${UUID.randomUUID()}",
                    "@opprettet": "${LocalDateTime.now()}",
                    "system_read_count": 0,
                    "@løsning": {
                        "MellomlagreRapportering": [
                            {
                                "metainfo": {
                                    "innhold": "netto.pdf",
                                    "filtype": "PDF",
                                    "variant": "NETTO"
                                },
                                "urn": "urn:vedlegg:journalpostId/netto.pdf"
                            }
                        ]
                    }
                }
                """.trimIndent()
            rapid.sendTestMessage(løstBehovMellomlagreRapportering)

            // RapporteringMellomlagretMottak må behandle MellomlagreRapportering-løsningen og opprette et nytt behov
            rapid.inspektør.size shouldBe 4
            behov = rapid.inspektør.message(3)
            behov.has("@behov") shouldBe true
            behov.get("@behov")[0].asText() shouldBe JournalføreRapportering.name
            behov.has(JournalføreRapportering.name) shouldBe true
            behov.get(JournalføreRapportering.name).get("periodeId").asText() shouldBe rapporteringsperiodeId.toString()
            behov.get(JournalføreRapportering.name).get("json").asText() shouldBe json

            val løstBehovJournalføreRapportering =
                """
                {
                    "@event_name": "behov",
                    "@behovId": "${UUID.randomUUID()}",
                    "@behov": [
                        "MellomlagreRapportering",
                        "JournalføreRapportering"
                    ],
                    "meldingsreferanseId": "${UUID.randomUUID()}",
                    "ident": "$testIdent",
                    "JournalføreRapportering": {
                        "periodeId": "$rapporteringsperiodeId",
                        "json": "${json.replace("\"", "\\\"")}",
                        "urn": "urn:vedlegg:journalpostId/netto.pdf"
                    },
                    "@id": "${UUID.randomUUID()}",
                    "@opprettet": "${LocalDateTime.now()}",
                    "system_read_count": 0,
                    "@løsning": {
                        "journalpostId": "123456"
                    }
                }
                """.trimIndent()
            rapid.sendTestMessage(løstBehovJournalføreRapportering)

            // Vi sender JournalføreRapportering-løsning, men det er fortsatt 4 meldinger
            // Det betyr det at RapporteringJournalførtMottak har behandlet løsningen
            rapid.inspektør.size shouldBe 4
        }
    }

    private val Person.aktivRapporteringsperiodeId get() = aktivRapporteringsperiode.rapporteringsperiodeId
    private val Person.aktivRapporteringsperiode get() = TestVisitor(this).rapporteringsperioder.last()

    private class TestVisitor(person: Person) : PersonVisitor {
        val rapporteringsperioder = mutableListOf<Rapporteringsperiode>()
        val aktiviteter = mutableListOf<Aktivitet>()

        init {
            person.accept(this)
        }

        override fun visit(
            rapporteringsperiode: Rapporteringsperiode,
            id: UUID,
            periode: ClosedRange<LocalDate>,
            tilstand: Rapporteringsperiode.TilstandType,
            beregnesEtter: LocalDate,
            korrigerer: Rapporteringsperiode?,
            korrigertAv: Rapporteringsperiode?,
        ) {
            rapporteringsperioder += rapporteringsperiode
        }

        override fun visit(
            dag: Dag,
            dato: LocalDate,
            aktiviteter: List<Aktivitet>,
            muligeAktiviter: List<Aktivitet.AktivitetType>,
            strategi: Dag.StrategiType,
        ) {
            this.aktiviteter += aktiviteter
        }
    }
}
