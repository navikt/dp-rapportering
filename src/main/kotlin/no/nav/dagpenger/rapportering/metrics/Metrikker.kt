package no.nav.dagpenger.rapportering.metrics
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.measureTime

private const val NAMESPACE = "dp_rapportering"

class MeldepliktMetrikker(
    meterRegistry: MeterRegistry,
) {
    val rapporteringApiFeil: Counter =
        Counter
            .builder("${NAMESPACE}_antall_meldeplikt_exception_total")
            .description("Indikerer antall feil i kall eller mapping av respons mot meldeplikt")
            .register(meterRegistry)

    val innsendteMeldekortOk: Counter =
        Counter
            .builder("${NAMESPACE}_innsendte_meldekort_ok_total")
            .description("Indikerer antall innsendte meldekort som ble kontrollert OK")
            .register(meterRegistry)

    val innsendteMeldekortIkkeOk: Counter =
        Counter
            .builder("${NAMESPACE}_innsendte_meldekort_not_ok_total")
            .description("Indikerer antall innsendte meldekort som ble kontrollert ikke OK")
            .register(meterRegistry)

    val innsendteMeldekortFeil: Counter =
        Counter
            .builder("${NAMESPACE}_innsendte_meldekort_feil_total")
            .description("Indikerer antall feill ved innsending av meldekort")
            .register(meterRegistry)

    val innsendteIngenAktiviteter: Counter =
        Counter
            .builder("${NAMESPACE}_innsendte_ingen_aktiviteter_total")
            .description("Indikerer antall innsendte meldekort uten aktiviteter")
            .register(meterRegistry)

    val innsendteEndring: Counter =
        Counter
            .builder("${NAMESPACE}_innsendte_endring_total")
            .description("Indikerer antall endrede innsendte meldekort")
            .register(meterRegistry)
}

class DatabaseMetrikker(
    meterRegistry: MeterRegistry,
) {
    private val lagredeRapporteringsperioder: AtomicInteger = AtomicInteger(0)
    private val midlertidigLagredeJournalposter: AtomicInteger = AtomicInteger(0)

    init {
        Gauge
            .builder("${NAMESPACE}_lagrede_rapporteringsperioder_total", lagredeRapporteringsperioder) { it.get().toDouble() }
            .description("Antall lagrede rapporteringsperioder i databasen")
            .register(meterRegistry)
        Gauge
            .builder("${NAMESPACE}_midlertidig_lagrede_journalposter_total", midlertidigLagredeJournalposter) { it.get().toDouble() }
            .description("Antall midlertidig lagrede journalposter i databasen")
            .register(meterRegistry)
    }

    fun oppdater(
        lagredeRapporteringsperioder: Int,
        midlertidigLagredeJournalposter: Int,
    ) {
        this.lagredeRapporteringsperioder.set(lagredeRapporteringsperioder)
        this.midlertidigLagredeJournalposter.set(midlertidigLagredeJournalposter)
    }
}

internal class JobbkjoringMetrikker(
    meterRegistry: MeterRegistry,
    navn: String,
) {
    private val jobStatus: Counter =
        Counter
            .builder("${NAMESPACE}_job_execution_status_total")
            .description("Indikerer status for kjøring av jobb")
            .tag("navn", navn)
            .register(meterRegistry)

    private val jobDuration: Timer =
        Timer
            .builder("${NAMESPACE}_job_execution_duration_seconds")
            .description("Varighet for kjøring av jobb i sekunder")
            .tag("navn", navn)
            .register(meterRegistry)

    private val affectedRowsCount: Counter =
        Counter
            .builder("${NAMESPACE}_affected_rows_count_total")
            .description("Antall rader påvirket av jobb")
            .tag("navn", navn)
            .register(meterRegistry)

    private val jobErrors: Counter =
        Counter
            .builder("${NAMESPACE}_job_errors_total")
            .description("Antall feil under kjøring av jobb")
            .tag("navn", navn)
            .register(meterRegistry)

    private fun incrementJobStatus(success: Boolean) = jobStatus.increment(if (success) 1.0 else 0.0)

    private fun observeJobDuration(durationSeconds: Number) = jobDuration.record(durationSeconds.toLong(), SECONDS)

    private fun incrementAffectedRowsCount(count: Number) = affectedRowsCount.increment(count.toDouble())

    private fun incrementJobErrors() = jobErrors.increment()

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

class ActionTimer(
    private val meterRegistry: MeterRegistry,
) {
    suspend fun <T> timedAction(
        navn: String,
        block: suspend () -> T,
    ): T {
        val blockResult: T
        val tidBrukt =
            measureTime {
                blockResult = block()
            }
        Timer
            .builder("${NAMESPACE}_timer")
            .tag("navn", navn)
            .description("Indikerer hvor lang tid en funksjon brukte")
            .register(meterRegistry)
            .record(tidBrukt.inWholeMilliseconds, MILLISECONDS)

        return blockResult
    }

    fun httpTimer(
        navn: String,
        statusCode: HttpStatusCode,
        method: HttpMethod,
        durationSeconds: Number,
    ) = Timer
        .builder("${NAMESPACE}_http_timer")
        .tag("navn", navn)
        .tag("status", statusCode.value.toString())
        .tag("method", method.value)
        .description("Indikerer hvor lang tid en funksjon brukte")
        .register(meterRegistry)
        .record(durationSeconds.toLong(), SECONDS)
}
