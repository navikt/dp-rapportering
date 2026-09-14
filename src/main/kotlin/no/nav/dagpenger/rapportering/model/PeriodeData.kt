package no.nav.dagpenger.rapportering.model

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.model.PeriodeData.PeriodeDag
import no.nav.dagpenger.rapportering.model.ÅrsakTilAtBrukerIkkeSkalSvarePåSpørsmålOmArbeidssøkerstatus.UKJENT_ÅRSAK_MELDEKORTET_ER_MIGRERT_FRA_ARENA
import no.nav.dagpenger.rapportering.utils.PeriodeUtils.kanSendesInn
import java.time.LocalDate
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

data class PeriodeData(
    val id: String,
    val ident: String,
    val periode: Periode,
    val dager: List<PeriodeDag>,
    val kanSendesFra: LocalDate,
    val sisteFristForTrekk: LocalDate,
    val opprettetAv: OpprettetAv,
    val kilde: Kilde?,
    val type: Type,
    val status: String = "Innsendt",
    val innsendtTidspunkt: LocalDateTime?,
    // Refererer til originalt meldekort ved korrigering
    val originalMeldekortId: String?,
    val bruttoBelop: Double? = null,
    val begrunnelse: String? = null,
    val registrertArbeidssoker: Boolean? = null,
    val meldedato: LocalDate? = null,
) {
    data class Kilde(
        val rolle: Rolle,
        val ident: String,
    )

    enum class Rolle {
        Bruker,
        Saksbehandler,
    }

    enum class Type {
        Ordinaert,
        Etterregistrert,
        Korrigert,
    }

    data class PeriodeDag(
        val dato: LocalDate,
        val aktiviteter: List<Aktivitet> = emptyList(),
        val dagIndex: Int,
    )
}

fun List<PeriodeData>?.toRapporteringsperioder(): List<Rapporteringsperiode> =
    this?.map {
        try {
            it.toRapporteringsperiode()
        } catch (e: Exception) {
            logger.error(e) { "Kunne ikke konvertere PeriodeData til Rapporteringsperiode: $it" }
            throw e
        }
    } ?: emptyList()

fun PeriodeData.toRapporteringsperiode(): Rapporteringsperiode {
    val status =
        when (this.status) {
            "TilUtfylling" -> RapporteringsperiodeStatus.TilUtfylling
            "Endret" -> RapporteringsperiodeStatus.Endret
            "Innsendt" -> RapporteringsperiodeStatus.Innsendt
            "Ferdig" -> RapporteringsperiodeStatus.Ferdig
            "Feilet" -> RapporteringsperiodeStatus.Feilet
            else -> throw IllegalStateException("Ukjent status '$status'")
        }

    return Rapporteringsperiode(
        id = this.id,
        type = KortType.valueOf(type.name),
        periode = Periode(fraOgMed = this.periode.fraOgMed, tilOgMed = this.periode.tilOgMed),
        dager = this.dager.map { it.toDag() },
        kanSendesFra = this.kanSendesFra,
        sisteFristForTrekk = this.sisteFristForTrekk,
        kanSendes = kanSendesInn(this.kanSendesFra, status, true),
        kanEndres = this.originalMeldekortId == null,
        bruttoBelop = this.bruttoBelop,
        status = status,
        mottattDato = this.innsendtTidspunkt?.toLocalDate(),
        begrunnelseEndring = if (this.begrunnelse.isNullOrBlank()) null else this.begrunnelse,
        registrertArbeidssoker = this.registrertArbeidssoker,
        // TODO: Denne kommer fra dp-meldekortregister, så her er det faktisk en verdi som kan brukes når det endepunktet i dp-meldekortregsiter er oppdatert så det returnerer verdien (RAMP-14)
        // TODO: Men verdien som kommer fra dp-meldekortregister utledes og sendes fra dp-rapportering, så høne og egget osv...
        årsakBrukerHarIkkeSvartePåSpørsmålOmArbeidssøkerstatus = UKJENT_ÅRSAK_MELDEKORTET_ER_MIGRERT_FRA_ARENA,
        originalId = this.originalMeldekortId,
        rapporteringstype = if (this.dager.any { it.aktiviteter.isNotEmpty() }) "harAktivitet" else "harIngenAktivitet",
        opprettetAv = OpprettetAv.Dagpenger,
    )
}

fun PeriodeDag.toDag(): Dag = Dag(dato = this.dato, aktiviteter = this.aktiviteter, dagIndex = this.dagIndex)
