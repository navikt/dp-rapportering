package no.nav.dagpenger.rapportering.metrics

import io.prometheus.client.Counter

private const val NAMESPACE = "dp-rapportering"

object RapporteringsperiodeMetrikker {
    val hentet: Counter =
        Counter.build()
            .namespace(NAMESPACE)
            .name("antall_personer_hentet")
            .help("Indikerer antall uthentede personer med rapporteringsperioder")
            .register()
}
