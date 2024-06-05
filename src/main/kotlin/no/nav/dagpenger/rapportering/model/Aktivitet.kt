package no.nav.dagpenger.rapportering.model

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.DurationUnit.HOURS
import kotlin.time.toDuration

data class Aktivitet(
    val uuid: UUID,
    val type: AktivitetsType,
    @field:JsonDeserialize(using = DoubleToIsoDurationStringDeserializer::class)
    val timer: String?,
) {
    enum class AktivitetsType {
        Arbeid,
        Syk,
        Utdanning,
        FerieEllerFravaer,
    }
}

class DoubleToIsoDurationStringDeserializer : JsonDeserializer<String?>() {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): String? =
        p.currentToken?.let {
            try {
                p.doubleValue.toDuration(HOURS).toIsoString()
            } catch (e: Exception) {
                Duration.parseIsoString(p.valueAsString).toIsoString()
            }
        }
}
