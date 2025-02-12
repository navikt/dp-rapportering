package no.nav.dagpenger.rapportering.kafka

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.Serializer
import java.util.Properties
import kotlin.reflect.KClass

class KafkaFactory(
    private val kafkaServerConfig: KafkaKonfigurasjon,
) {
    private val baseProperties =
        Properties().apply {
            putAll(kafkaServerConfig.properties)
        }

    fun <K : Any, V : Any> createProducer(
        clientId: String,
        keySerializer: KClass<out Serializer<K>>,
        valueSerializer: KClass<out Serializer<V>>,
        acks: String = "all",
    ): Producer<K, V> =
        KafkaProducer(
            baseProperties +
                mapOf(
                    ProducerConfig.ACKS_CONFIG to acks,
                    ProducerConfig.CLIENT_ID_CONFIG to clientId,
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to keySerializer.java,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to valueSerializer.java,
                ),
        )
}

operator fun Properties.plus(other: Map<String, Any>): Properties =
    Properties().apply {
        putAll(this@plus)
        putAll(other)
    }

fun <K, V> Producer<K, V>.sendDeferred(record: ProducerRecord<K, V>): Deferred<RecordMetadata> {
    val deferred = CompletableDeferred<RecordMetadata>()
    send(record) { metadata, exception ->
        if (exception != null) {
            deferred.completeExceptionally(exception)
        } else {
            deferred.complete(metadata)
        }
    }
    return deferred
}

class BekreftelseAvroSerializer : SpecificAvroSerializer<Bekreftelse>()
