package no.nav.dagpenger.rapportering.utils

import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.dagpenger.rapportering.metrics.ActionTimer
import no.nav.dagpenger.rapportering.metrics.MeldepliktMetrikker

object MetricsTestUtil {
    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, PrometheusRegistry.defaultRegistry, Clock.SYSTEM)
    val meldepliktMetrikker = MeldepliktMetrikker(meterRegistry)
    val actionTimer = ActionTimer(meterRegistry)
}
