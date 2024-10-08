package no.nav.dagpenger.rapportering.model

import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Arbeid
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Fravaer
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Syk
import java.time.LocalDate
import kotlin.time.Duration

data class Dag(
    val dato: LocalDate,
    val aktiviteter: List<Aktivitet> = emptyList(),
    val dagIndex: Int,
) {
    init {
        require(aktiviteter.validerIngenDuplikateAktivitetsTyper()) {
            "Duplikate Aktivitetstyper er ikke tillatt i aktivitetslisten"
        }
        require(aktiviteter.validerAktivitetsTypeKombinasjoner()) {
            "Aktivitetene Syk og Arbeid, samt Fravær og Arbeid kan ikke kombineres."
        }
        require(aktiviteter.validerArbeidedeTimer()) {
            "Arbeidede timer kan ikke være null, 0 eller over 24 timer. Kun hele og halve timer er gyldig input"
        }
        require(aktiviteter.validerIngenArbeidedeTimerUtenArbeid()) {
            "Aktiviteter som ikke er arbeid kan ikke ha utfylte arbeidede timer"
        }
    }

    private fun List<Aktivitet>.validerIngenDuplikateAktivitetsTyper(): Boolean =
        this
            .map { it.type }
            .toSet()
            .size == this.size

    private fun List<Aktivitet>.validerAktivitetsTypeKombinasjoner(): Boolean =
        this
            .map { it.type }
            .let { typer ->
                if (typer.contains(Syk) && typer.contains(Arbeid)) {
                    false
                } else if (typer.contains(Fravaer) && typer.contains(Arbeid)) {
                    false
                } else {
                    true
                }
            }

    private fun List<Aktivitet>.validerArbeidedeTimer(): Boolean =
        this
            .filter { it.type == Arbeid }
            .all {
                if (it.timer == null) return false
                try {
                    val arbeidedeTimer = Duration.parseIsoString(it.timer)
                    val timer = arbeidedeTimer.inWholeHours
                    val minutter = arbeidedeTimer.inWholeMinutes % 60
                    timer <= 24 && (minutter == 0L || minutter == 30L) && (timer + minutter != 0L)
                } catch (e: Exception) {
                    false
                }
            }

    private fun List<Aktivitet>.validerIngenArbeidedeTimerUtenArbeid(): Boolean =
        this
            .filter { it.type != Arbeid }
            .all { it.timer == null }
}
