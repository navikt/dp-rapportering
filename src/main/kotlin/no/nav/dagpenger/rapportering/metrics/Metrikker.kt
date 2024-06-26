package no.nav.dagpenger.rapportering.metrics
import io.prometheus.client.Counter
import no.nav.dagpenger.rapportering.Configuration.appMicrometerRegistry

private const val NAMESPACE = "dp_rapportering"

object RapporteringsperiodeMetrikker {
    val hentet: Counter =
        Counter.build()
            .namespace(NAMESPACE)
            .name("antall_personer_hentet")
            .help("Indikerer antall uthentede personer med rapporteringsperioder")
            .register(appMicrometerRegistry.prometheusRegistry)
}

object MeldepliktMetrikker {
    val meldepliktError: Counter =
        Counter.build()
            .namespace(NAMESPACE)
            .name("antall_meldeplikt_feil_status")
            .help("Indikerer antall kall mot meldeplikt som gir en annen http status enn 200 OK")
            .register(appMicrometerRegistry.prometheusRegistry)

    val meldepliktException: Counter =
        Counter.build()
            .namespace(NAMESPACE)
            .name("antall_meldeplikt_exception")
            .help("Indikerer antall feil i kall eller mapping av respons mot meldeplikt")
            .register(appMicrometerRegistry.prometheusRegistry)
}
