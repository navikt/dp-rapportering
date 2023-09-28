package no.nav.dagpenger.rapportering

import no.nav.dagpenger.aktivitetslogg.Aktivitet

enum class MineBehov : Aktivitet.Behov.Behovtype {
    Søknadstidspunkt,
    JournalføreRapportering,
    OpprettPdfForRapportering,
}
