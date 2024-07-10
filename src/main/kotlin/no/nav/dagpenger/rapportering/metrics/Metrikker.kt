package no.nav.dagpenger.rapportering.metrics
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Histogram
import no.nav.dagpenger.rapportering.Configuration.appMicrometerRegistry
import kotlin.time.Duration

private const val NAMESPACE = "dp_rapportering"

object RapporteringsperiodeMetrikker {
    val hentet: Counter =
        Counter
            .build()
            .namespace(NAMESPACE)
            .name("antall_personer_hentet")
            .help("Indikerer antall uthentede personer med rapporteringsperioder")
            .register(appMicrometerRegistry.prometheusRegistry)
}

object MeldepliktMetrikker {
    val meldepliktError: Counter =
        Counter
            .build()
            .namespace(NAMESPACE)
            .name("antall_meldeplikt_feil_status")
            .help("Indikerer antall kall mot meldeplikt som gir en annen http status enn 200 OK")
            .register(appMicrometerRegistry.prometheusRegistry)

    val rapporteringApiFeil: Counter =
        Counter
            .build()
            .namespace(NAMESPACE)
            .name("antall_meldeplikt_exception")
            .help("Indikerer antall feil i kall eller mapping av respons mot meldeplikt")
            .register(appMicrometerRegistry.prometheusRegistry)
}

object DatabaseMetrikker {
    val lagredeRapporteringsperioder: Gauge =
        Gauge
            .build()
            .namespace(NAMESPACE)
            .name("lagrede_rapporteringsperioder")
            .help("Antall lagrede rapporteringsperioder i databasen")
            .register(appMicrometerRegistry.prometheusRegistry)

    val lagredeJournalposter: Gauge =
        Gauge
            .build()
            .namespace(NAMESPACE)
            .name("lagrede_journalposter")
            .help("Antall lagrede journalposter i databasen")
            .register(appMicrometerRegistry.prometheusRegistry)

    val midlertidigLagredeJournalposter: Gauge =
        Gauge
            .build()
            .namespace(NAMESPACE)
            .name("midlertidig_lagrede_journalposter")
            .help("Antall midlertidig lagrede journalposter i databasen")
            .register(appMicrometerRegistry.prometheusRegistry)
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
                .register(appMicrometerRegistry.prometheusRegistry)

        private val jobDuration: Histogram =
            Histogram
                .build()
                .namespace(NAMESPACE)
                .name("job_execution_duration_seconds")
                .help("Varighet for kjøring av jobb i sekunder")
                .labelNames("navn")
                .register(appMicrometerRegistry.prometheusRegistry)

        private val affectedRowsCount: Counter =
            Counter
                .build()
                .namespace(NAMESPACE)
                .name("affected_rows_count")
                .help("Antall rader påvirket av jobb")
                .labelNames("navn")
                .register(appMicrometerRegistry.prometheusRegistry)

        private val jobErrors: Counter =
            Counter
                .build()
                .namespace(NAMESPACE)
                .name("job_errors_total")
                .help("Antall feil under kjøring av jobb")
                .labelNames("navn")
                .register(appMicrometerRegistry.prometheusRegistry)
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
