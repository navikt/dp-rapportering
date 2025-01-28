package no.nav.dagpenger.rapportering.service

import com.natpryce.konfig.Key
import com.natpryce.konfig.stringType
import no.nav.dagpenger.rapportering.config.Configuration.properties
import no.nav.dagpenger.rapportering.model.KallLogg
import no.nav.dagpenger.rapportering.repository.KallLoggRepository
import no.nav.dagpenger.rapportering.utils.getCallId
import java.time.LocalDateTime

class KallLoggService(
    private val kallLoggRepository: KallLoggRepository,
) {
    fun lagreKafkaUtKallLogg(ident: String): Long {
        return kallLoggRepository.lagreKallLogg(
            KallLogg(
                korrelasjonId = getCallId(),
                tidspunkt = LocalDateTime.now(),
                type = "KAFKA",
                kallRetning = "UT",
                method = "PUBLISH",
                operation = properties[Key("KAFKA_RAPID_TOPIC", stringType)],
                status = 200,
                kallTid = 0,
                request = "",
                response = "",
                ident = ident,
                logginfo = "",
            ),
        )
    }

    fun lagreRequest(
        kallLoggId: Long,
        request: String,
    ) {
        kallLoggRepository.lagreRequest(kallLoggId, request)
    }

    fun lagreResponse(
        kallLoggId: Long,
        status: Int,
        response: String,
    ) {
        kallLoggRepository.lagreResponse(kallLoggId, status, response)
    }
}
