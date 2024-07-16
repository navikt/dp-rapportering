package no.nav.dagpenger.rapportering.metrics
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Histogram
import kotlin.time.Duration
import kotlin.time.measureTime

private const val NAMESPACE = "dp_rapportering"

object RapporteringsperiodeMetrikker {
    val hentet: Counter =
        Counter
            .build()
            .namespace(NAMESPACE)
            .name("antall_personer_hentet")
            .help("Indikerer antall uthentede personer med rapporteringsperioder")
            .register()
}

object MeldepliktMetrikker {
    val meldepliktError: Counter =
        Counter
            .build()
            .namespace(NAMESPACE)
            .name("antall_meldeplikt_feil_status")
            .help("Indikerer antall kall mot meldeplikt som gir en annen http status enn 200 OK")
            .register()

    val rapporteringApiFeil: Counter =
        Counter
            .build()
            .namespace(NAMESPACE)
            .name("antall_meldeplikt_exception")
            .help("Indikerer antall feil i kall eller mapping av respons mot meldeplikt")
            .register()
}

object DatabaseMetrikker {
    val lagredeRapporteringsperioder: Gauge =
        Gauge
            .build()
            .namespace(NAMESPACE)
            .name("lagrede_rapporteringsperioder_total")
            .help("Antall lagrede rapporteringsperioder i databasen")
            .register()

    val lagredeJournalposter: Gauge =
        Gauge
            .build()
            .namespace(NAMESPACE)
            .name("lagrede_journalposter_total")
            .help("Antall lagrede journalposter i databasen")
            .register()

    val midlertidigLagredeJournalposter: Gauge =
        Gauge
            .build()
            .namespace(NAMESPACE)
            .name("midlertidig_lagrede_journalposter_total")
            .help("Antall midlertidig lagrede journalposter i databasen")
            .register()
}

internal class JobbkjoringMetrikker(
    private val navn: String,
) {
    companion object {
        private val jobStatus: Counter =
            Counter
                .build()
                .namespace(NAMESPACE)
                .name("job_execution_status")
                .help("Indikerer status for kjøring av jobb")
                .labelNames("navn")
                .register()

        private val jobDuration: Histogram =
            Histogram
                .build()
                .namespace(NAMESPACE)
                .name("job_execution_duration_seconds")
                .help("Varighet for kjøring av jobb i sekunder")
                .labelNames("navn")
                .register()

        private val affectedRowsCount: Counter =
            Counter
                .build()
                .namespace(NAMESPACE)
                .name("affected_rows_count")
                .help("Antall rader påvirket av jobb")
                .labelNames("navn")
                .register()

        private val jobErrors: Counter =
            Counter
                .build()
                .namespace(NAMESPACE)
                .name("job_errors_total")
                .help("Antall feil under kjøring av jobb")
                .labelNames("navn")
                .register()
    }

    private fun incrementJobStatus(success: Boolean) = jobStatus.labels(navn).inc(if (success) 1.0 else 0.0)

    private fun observeJobDuration(durationSeconds: Number) = jobDuration.labels(navn).observe(durationSeconds.toDouble())

    private fun incrementAffectedRowsCount(count: Number) = affectedRowsCount.labels(navn).inc(count.toDouble())

    private fun incrementJobErrors() = jobErrors.labels(navn).inc()

    fun jobbFeilet() {
        incrementJobStatus(false)
        incrementJobErrors()
    }

    fun jobbFullfort(
        duration: Duration,
        affectedRows: Int,
    ) {
        incrementJobStatus(true)
        observeJobDuration(duration.inWholeSeconds)
        incrementAffectedRowsCount(affectedRows)
    }
}

object TimedMetrikk {
    val timer: Histogram =
        Histogram
            .build()
            .namespace(NAMESPACE)
            .name("timer")
            .help("Indikerer hvor lang tid en funksjon brukte")
            .labelNames("navn")
            .register()

    suspend fun <T> timedAction(
        navn: String,
        block: suspend () -> T,
    ): T {
        val blockResult: T
        val tidBrukt =
            measureTime {
                blockResult = block()
            }
        timer.labels(navn).observe(tidBrukt.inWholeSeconds.toDouble())
        return blockResult
    }

    val httpTimer: Histogram =
        Histogram
            .build()
            .namespace(NAMESPACE)
            .name("http_timer")
            .help("Indikerer hvor lang tid et http-brukte")
            .labelNames("navn", "status", "method")
            .register()

    fun httpTimer(
        navn: String,
        statusCode: HttpStatusCode,
        method: HttpMethod,
        durationSeconds: Number,
    ) = httpTimer.labels(navn, "$statusCode", method.value).observe(durationSeconds.toDouble())
}
