package no.nav.dagpenger.rapportering.kafka

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaProdusent<T>(
    private val kafkaProducer: KafkaProducer<Long, String>,
    val topic: String,
) {
    protected fun serialize(value: T): String = defaultObjectMapper.writeValueAsString(value)

    fun send(
        key: Long,
        value: T,
    ) {
        val record = ProducerRecord(topic, key, serialize(value))
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
