package no.nav.dagpenger.rapportering

import no.nav.dagpenger.aktivitetslogg.Aktivitet
import no.nav.dagpenger.aktivitetslogg.IAktivitetslogg
import java.time.LocalDate

enum class MineBehov : Aktivitet.Behov.Behovtype {
    Sykepengehistorikk,
    Foreldrepenger,
}

object Foobar {
    fun utbetalingshistorikk(
        aktivitetslogg: IAktivitetslogg,
        periode: ClosedRange<LocalDate>,
    ) {
        aktivitetslogg.behov(
            MineBehov.Sykepengehistorikk,
            "Trenger sykepengehistorikk fra Infotrygd",
            mapOf(
                "historikkFom" to periode.start.toString(),
                "historikkTom" to periode.endInclusive.toString(),
            ),
        )
    }

    fun foreldrepenger(aktivitetslogg: IAktivitetslogg, periode: ClosedRange<LocalDate>) {
        aktivitetslogg.behov(
            MineBehov.Foreldrepenger,
            "Trenger informasjon om foreldrepengeytelser fra FPSAK",
            mapOf(
                "foreldrepengerFom" to periode.start.toString(),
                "foreldrepengerTom" to periode.endInclusive.toString(),
            ),
        )
    }
}
