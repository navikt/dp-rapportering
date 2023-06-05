package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag

interface Rapporteringsplikt {
    val type: Rapporteringsplikttype
    fun valider(aktivitet: Aktivitet): Boolean
    fun valider(dag: Dag): Boolean
}

enum class Rapporteringsplikttype {
    Ingen,
    Søknad,
    Vedtak,
}

class RapporteringspliktSøknad : Rapporteringsplikt {
    override val type: Rapporteringsplikttype
        get() = Rapporteringsplikttype.Søknad

    override fun valider(aktivitet: Aktivitet): Boolean {
        TODO("Not yet implemented")
    }

    override fun valider(dag: Dag): Boolean {
        TODO("Not yet implemented")
    }
}

class RapporteringspliktVedtak(private val normalFastsattArbeidstid: Double) : Rapporteringsplikt {
    override val type: Rapporteringsplikttype
        get() = Rapporteringsplikttype.Vedtak

    override fun valider(aktivitet: Aktivitet): Boolean {
        TODO("Not yet implemented")
    }

    override fun valider(dag: Dag): Boolean {
        TODO("Not yet implemented")
    }
}
