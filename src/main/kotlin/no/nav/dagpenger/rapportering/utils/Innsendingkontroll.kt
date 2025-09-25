package no.nav.dagpenger.rapportering.utils

import io.ktor.server.plugins.BadRequestException
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Arbeid
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Fravaer
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Syk
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import java.time.LocalDate
import kotlin.time.Duration

fun kontrollerRapporteringsperiode(periode: Rapporteringsperiode) {
    if (!periode.kanSendes) {
        throw BadRequestException("Rapporteringsperiode med id ${periode.id} kan ikke sendes")
    } else if (periode.kanSendesFra.isAfter(LocalDate.now())) {
        throw BadRequestException(
            "Rapporteringsperiode med id ${periode.id} kan ikke sendes før kan sendes fra dato (${periode.kanSendesFra})",
        )
    } else if (periode.registrertArbeidssoker == null) {
        throw BadRequestException("Registrert arbeidssøker kan ikke være null")
    } else {
        kontrollerAktiviteter(periode.dager)
    }
}

fun kontrollerAktiviteter(dager: List<Dag>) {
    dager.forEach { dag ->
        if (!dag.aktiviteter.validerIngenDuplikateAktivitetsTyper()) {
            throw BadRequestException("Duplikate Aktivitetstyper er ikke tillatt i aktivitetslisten")
        } else if (!dag.aktiviteter.validerAktivitetsTypeKombinasjoner()) {
            throw BadRequestException("Aktivitetene Syk og Arbeid, samt Fravær og Arbeid kan ikke kombineres.")
        } else if (!dag.aktiviteter.validerArbeidedeTimer()) {
            throw BadRequestException("Arbeidede timer kan ikke være null, 0 eller over 24 timer. Kun hele og halve timer er gyldig input")
        } else if (!dag.aktiviteter.validerIngenArbeidedeTimerUtenArbeid()) {
            throw BadRequestException("Aktiviteter som ikke er arbeid kan ikke ha utfylte arbeidede timer")
        }
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
