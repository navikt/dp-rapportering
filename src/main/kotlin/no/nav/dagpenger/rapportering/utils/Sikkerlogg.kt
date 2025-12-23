package no.nav.dagpenger.rapportering.utils

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KMarkerFactory
import io.github.oshai.kotlinlogging.KotlinLogging

// Se https://docs.nais.io/observability/logging/how-to/team-logs/ for konfigurasjon
object Sikkerlogg {
    private val sikkerLogger: KLogger = KotlinLogging.logger("team-logs-logger")
    private val sikkerMarker = KMarkerFactory.getMarker("TEAM_LOGS")

    fun debug(
        throwable: Throwable? = null,
        loggstatement: () -> Any?,
    ) {
        sikkerLogger.debug(throwable = throwable, marker = sikkerMarker, message = loggstatement)
    }

    fun info(
        throwable: Throwable? = null,
        loggstatement: () -> Any?,
    ) {
        sikkerLogger.info(throwable = throwable, marker = sikkerMarker, message = loggstatement)
    }

    fun warn(
        throwable: Throwable? = null,
        loggstatement: () -> Any?,
    ) {
        sikkerLogger.warn(throwable = throwable, marker = sikkerMarker, message = loggstatement)
    }

    fun error(
        throwable: Throwable? = null,
        loggstatement: () -> Any?,
    ) {
        sikkerLogger.error(throwable = throwable, marker = sikkerMarker, message = loggstatement)
    }
}
