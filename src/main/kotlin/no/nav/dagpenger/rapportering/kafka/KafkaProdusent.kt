package no.nav.dagpenger.rapportering.kafka

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer
import mu.KotlinLogging
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaProdusent(
    private val kafkaProducer: KafkaProducer<Long, Bekreftelse>,
    val topic: String,
) {
    fun send(
        key: Long,
        value: Bekreftelse,
    ) {
        val record = ProducerRecord(topic, key, value)

        kafkaProducer.send(record) { metadata, exception ->
            if (exception != null) {
                logger.error { "Kunne ikke sende melding: Nøkkel=$key, Verdi=$value, Feil=${exception.message}" }
            } else {
                logger.info { "Melding sendt: Nøkkel=$key, Verdi=$value til Topic=${metadata.topic()} på Offset=${metadata.offset()}" }
            }
        }
    }

    fun close() {
        kafkaProducer.close()
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

class BekreftelseAvroSerializer : SpecificAvroSerializer<Bekreftelse>()
