package no.nav.dagpenger.rapportering.metrikker

import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import kotlin.time.Duration

private const val NAMESPACE = "dp_rapportering"

internal class JobbKjøringMetrikker(private val navn: String) {
    // Define the metrics
    private companion object {
        private val jobStatus: Counter =
            Counter.build()
                .namespace(NAMESPACE)
                .name("job_execution_status")
                .help("Indicates the status of the job execution")
                .labelNames("navn")
                .register()

        private val jobDuration: Histogram =
            Histogram.build()
                .namespace(NAMESPACE)
                .name("job_execution_duration_seconds")
                .help("Duration of the job execution in seconds")
                .labelNames("navn")
                .register()

        private val affectedRowsCount: Counter =
            Counter.build()
                .namespace(NAMESPACE)
                .name("affected_rows_count")
                .help("Number of rows affected by the job")
                .labelNames("navn")
                .register()

        private val jobErrors: Counter =
            Counter.build()
                .namespace(NAMESPACE)
                .name("job_errors_total")
                .help("Number of errors encountered during the job execution")
                .labelNames("navn")
                .register()
    }

    private fun incrementJobStatus(success: Boolean) {
        jobStatus.labels(navn).inc(if (success) 1.0 else 0.0)
    }

    private fun observeJobDuration(durationSeconds: Number) {
        jobDuration.labels(navn).observe(durationSeconds.toDouble())
    }

    private fun incrementAffectedRowsCount(count: Number) {
        affectedRowsCount.labels(navn).inc(count.toDouble())
    }

    private fun incrementJobErrors() {
        jobErrors.labels(navn).inc()
    }

    fun jobbFeilet() {
        incrementJobStatus(false)
        incrementJobErrors()
    }

    fun jobbFullført(
        tidBrukt: Duration,
        rader: Int,
    ) {
        observeJobDuration(tidBrukt.inWholeSeconds)
        incrementJobStatus(true)
        incrementAffectedRowsCount(rader)
    }
}
