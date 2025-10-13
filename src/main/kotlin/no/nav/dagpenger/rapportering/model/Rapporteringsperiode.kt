package no.nav.dagpenger.rapportering.model

import no.nav.dagpenger.rapportering.api.models.AktivitetResponse
import no.nav.dagpenger.rapportering.api.models.AktivitetTypeResponse
import no.nav.dagpenger.rapportering.api.models.DagInnerResponse
import no.nav.dagpenger.rapportering.api.models.PeriodeResponse
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeResponse
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeStatusResponse
import no.nav.dagpenger.rapportering.model.PeriodeData.Kilde
import no.nav.dagpenger.rapportering.model.PeriodeData.OpprettetAv
import no.nav.dagpenger.rapportering.model.PeriodeData.PeriodeDag
import no.nav.dagpenger.rapportering.model.PeriodeData.Type
import java.time.LocalDate
import java.time.LocalDateTime

data class Rapporteringsperiode(
    val id: String,
    val type: String,
    val periode: Periode,
    val dager: List<Dag>,
    val kanSendesFra: LocalDate,
    val sisteFristForTrekk: LocalDate = periode.tilOgMed.plusDays(8),
    val kanSendes: Boolean,
    val kanEndres: Boolean,
    val bruttoBelop: Double?,
    val begrunnelseEndring: String?,
    val status: RapporteringsperiodeStatus,
    val mottattDato: LocalDate?,
    val registrertArbeidssoker: Boolean?,
    val originalId: String?,
    val rapporteringstype: String?,
    val html: String? = null,
)

fun Rapporteringsperiode.erEndring(): Boolean = this.status == RapporteringsperiodeStatus.TilUtfylling && this.originalId != null

fun Rapporteringsperiode.arbeidet(): Boolean =
    this.dager.find { dag -> dag.aktiviteter.find { a -> a.type == Aktivitet.AktivitetsType.Arbeid } != null } != null

fun List<Rapporteringsperiode>.toResponse(): List<RapporteringsperiodeResponse> =
    this.map { rapporteringsperiode -> rapporteringsperiode.toResponse() }

fun Rapporteringsperiode.toResponse(): RapporteringsperiodeResponse =
    RapporteringsperiodeResponse(
        id = this.id,
        type = this.type,
        periode =
            PeriodeResponse(
                fraOgMed = this.periode.fraOgMed,
                tilOgMed = this.periode.tilOgMed,
            ),
        dager =
            dager.map { dag ->
                DagInnerResponse(
                    dato = dag.dato,
                    aktiviteter =
                        dag.aktiviteter.map { aktivitet ->
                            AktivitetResponse(
                                id = aktivitet.id,
                                type =
                                    when (aktivitet.type) {
                                        Aktivitet.AktivitetsType.Arbeid -> AktivitetTypeResponse.Arbeid
                                        Aktivitet.AktivitetsType.Syk -> AktivitetTypeResponse.Syk
                                        Aktivitet.AktivitetsType.Utdanning -> AktivitetTypeResponse.Utdanning
                                        Aktivitet.AktivitetsType.Fravaer -> AktivitetTypeResponse.Fravaer
                                    },
                                timer = aktivitet.timer,
                            )
                        },
                    dagIndex = dag.dagIndex.toBigDecimal(),
                )
            },
        sisteFristForTrekk = this.sisteFristForTrekk,
        kanSendesFra = this.kanSendesFra,
        kanSendes = this.kanSendes,
        kanEndres = this.kanEndres,
        begrunnelseEndring = this.begrunnelseEndring,
        bruttoBelop = this.bruttoBelop?.toBigDecimal(),
        status =
            when (this.status) {
                RapporteringsperiodeStatus.TilUtfylling -> RapporteringsperiodeStatusResponse.TilUtfylling
                RapporteringsperiodeStatus.Endret -> RapporteringsperiodeStatusResponse.Endret
                RapporteringsperiodeStatus.Innsendt -> RapporteringsperiodeStatusResponse.Innsendt
                RapporteringsperiodeStatus.Ferdig -> RapporteringsperiodeStatusResponse.Ferdig
                RapporteringsperiodeStatus.Feilet -> RapporteringsperiodeStatusResponse.Feilet
                RapporteringsperiodeStatus.Midlertidig -> RapporteringsperiodeStatusResponse.Feilet // Midlertidig her? Det er en feil
            },
        mottattDato = this.mottattDato,
        registrertArbeidssoker = this.registrertArbeidssoker,
        originalId = this.originalId,
        rapporteringstype = this.rapporteringstype,
    )

fun Rapporteringsperiode.toPeriodeData(
    ident: String,
    opprettetAv: OpprettetAv,
    arbeidssøkerperioder: List<ArbeidssøkerperiodeResponse>,
    nyStatus: String? = null,
): PeriodeData =
    PeriodeData(
        id = this.id,
        ident = ident,
        periode = this.periode,
        dager = this.dager.toPeriodeDager(arbeidssøkerperioder),
        kanSendesFra = this.kanSendesFra,
        opprettetAv = opprettetAv,
        kilde = Kilde(PeriodeData.Rolle.Bruker, ident),
        type = if (this.erEndring()) Type.Korrigert else Type.Original,
        status = nyStatus ?: this.status.name,
        innsendtTidspunkt = LocalDateTime.now(),
        originalMeldekortId = this.originalId,
        bruttoBelop = null,
        begrunnelse = this.begrunnelseEndring,
        registrertArbeidssoker = this.registrertArbeidssoker,
    )

fun List<Dag>.toPeriodeDager(arbeidssøkerperioder: List<ArbeidssøkerperiodeResponse>): List<PeriodeDag> =
    this.map {
        PeriodeDag(
            dato = it.dato,
            aktiviteter = it.aktiviteter,
            dagIndex = it.dagIndex,
            meldt =
                arbeidssøkerperioder.find { periode ->
                    val fom = periode.startet.tidspunkt
                    val tom = periode.avsluttet
                    !fom.toLocalDate().isAfter(it.dato) &&
                        (tom == null || !tom.tidspunkt.toLocalDate().isBefore(it.dato))
                } != null,
        )
    }

fun PeriodeData.toKorrigerMeldekortHendelse(): KorrigerMeldekortHendelse =
    KorrigerMeldekortHendelse(
        ident = ident,
        originalMeldekortId =
            originalMeldekortId
                ?: throw IllegalStateException("originalMeldekortId kan ikke være null ved opprettelse av KorrigerMeldekortHendelse"),
        periode = periode,
        dager = dager,
        kilde = kilde ?: throw IllegalStateException("kilde kan ikke være null ved opprettelse av KorrigerMeldekortHendelse"),
        begrunnelse =
            begrunnelse
                ?: throw IllegalStateException("begrunnelse kan ikke være null ved opprettelse av KorrigerMeldekortHendelse"),
    )
