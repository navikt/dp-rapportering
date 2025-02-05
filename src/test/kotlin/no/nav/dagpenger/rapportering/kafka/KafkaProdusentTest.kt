package no.nav.dagpenger.rapportering.kafka

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import no.nav.paw.bekreftelse.melding.v1.vo.Bekreftelsesloesning
import no.nav.paw.bekreftelse.melding.v1.vo.Bruker
import no.nav.paw.bekreftelse.melding.v1.vo.BrukerType
import no.nav.paw.bekreftelse.melding.v1.vo.Metadata
import no.nav.paw.bekreftelse.melding.v1.vo.Svar
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Future

class KafkaProdusentTest {
    @Test
    fun `Kan sende Bekreftelse`() {
        val topic = "testTopic"
        val periodeId = UUID.randomUUID()
        val ident = "01020312345"
        val aarsak = "Bruker sendte inn dagpengermeldekort"

        val slot = slot<ProducerRecord<Long, Bekreftelse>>()
        val kafkaProducer = mockk<KafkaProducer<Long, Bekreftelse>>()
        every { kafkaProducer.send(capture(slot), any()) } returns mockk<Future<RecordMetadata>>()

        val kafkaProdusent = KafkaProdusent(kafkaProducer, topic)

        val key = 123456789L
        kafkaProdusent.send(
            key,
            Bekreftelse(
                periodeId,
                Bekreftelsesloesning.DAGPENGER,
                UUID.randomUUID(),
                Svar(
                    Metadata(
                        LocalDateTime.now().toInstant(ZoneOffset.UTC),
                        Bruker(BrukerType.SLUTTBRUKER, ident),
                        Bekreftelsesloesning.DAGPENGER.name,
                        "Bruker sendte inn dagpengermeldekort",
                    ),
                    LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC),
                    LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC),
                    true,
                    true,
                ),
            ),
        )

        slot.captured.key() shouldBe key
        slot.captured.topic() shouldBe topic

        val sentValue = slot.captured.value()
        sentValue.periodeId shouldBe periodeId
        sentValue.bekreftelsesloesning shouldBe Bekreftelsesloesning.DAGPENGER
        sentValue.svar.sendtInnAv.utfoertAv.type shouldBe BrukerType.SLUTTBRUKER
        sentValue.svar.sendtInnAv.utfoertAv.id shouldBe ident
        sentValue.svar.sendtInnAv.kilde shouldBe Bekreftelsesloesning.DAGPENGER.name
        sentValue.svar.sendtInnAv.aarsak shouldBe aarsak
        sentValue.svar.gjelderFra shouldBe LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)
        sentValue.svar.gjelderTil shouldBe LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)
        sentValue.svar.harJobbetIDennePerioden shouldBe true
        sentValue.svar.vilFortsetteSomArbeidssoeker shouldBe true
    }
}
