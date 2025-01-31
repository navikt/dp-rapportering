package no.nav.dagpenger.rapportering.kafka

import io.confluent.kafka.serializers.KafkaAvroSerializer
import mu.KotlinLogging
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaProdusent<T>(
    private val kafkaProducer: KafkaProducer<Long, Any>,
    val topic: String,
) {
    fun send(
        key: Long,
        value: T,
    ) {
        val valueSerialized = KafkaAvroSerializer().serialize(topic, value)

        val record = ProducerRecord<Long, Any>(topic, key, valueSerialized)

        kafkaProducer.send(record) { metadata, exception ->
            if (exception != null) {
                logger.info { "Kunne ikke sende melding: Nøkkel=$key, Verdi=$value, Feil=${exception.message}" }
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
