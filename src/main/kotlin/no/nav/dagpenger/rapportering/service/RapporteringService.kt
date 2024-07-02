package no.nav.dagpenger.rapportering.service

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.connector.toAdapterRapporteringsperiode
import no.nav.dagpenger.rapportering.connector.toRapporteringsperioder
import no.nav.dagpenger.rapportering.metrics.RapporteringsperiodeMetrikker
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.PeriodeId
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Innsendt
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Korrigert
import no.nav.dagpenger.rapportering.repository.RapporteringRepository
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

class RapporteringService(
    private val meldepliktConnector: MeldepliktConnector,
    private val rapporteringRepository: RapporteringRepository,
    private val journalfoeringService: JournalfoeringService,
) {
    suspend fun hentPeriode(
        rapporteringId: Long,
        ident: String,
        token: String,
    ): Rapporteringsperiode? =
        hentRapporteringsperioder(ident, token)
            ?.firstOrNull { it.id == rapporteringId }
            ?.let { lagreEllerOppdaterPeriode(it, ident) }
            ?: hentInnsendteRapporteringsperioder(ident, token)
                .firstOrNull { it.id == rapporteringId }
            ?: rapporteringRepository
                .hentRapporteringsperiode(rapporteringId, ident)

    suspend fun hentAllePerioderSomKanSendes(
        ident: String,
        token: String,
    ): List<Rapporteringsperiode>? =
        hentRapporteringsperioder(ident, token)
            ?.filter { it.kanSendes }
            ?.map { periode ->
                if (rapporteringRepository.hentRapporteringsperiode(periode.id, ident) != null) {
                    rapporteringRepository.oppdaterRapporteringsperiodeFraArena(periode, ident)
                    rapporteringRepository.hentRapporteringsperiode(periode.id, ident)
                        ?: throw RuntimeException("Fant ikke rapporteringsperiode, selv om den er lagret")
                } else {
                    periode
                }
            }?.sortedBy { it.periode.fraOgMed }
            .also { RapporteringsperiodeMetrikker.hentet.inc() }

    private suspend fun hentRapporteringsperioder(
        ident: String,
        token: String,
    ): List<Rapporteringsperiode>? =
        meldepliktConnector
            .hentRapporteringsperioder(ident, token)
            ?.toRapporteringsperioder()
            ?.filter { periode ->
                // Filtrerer ut perioder som har en høyere status i databasen enn det vi får fra arena
                rapporteringRepository
                    .hentRapporteringsperiode(periode.id, ident)
                    ?.let { periodeFraDb ->
                        periodeFraDb.status.ordinal <= periode.status.ordinal
                    } ?: true
            }

    suspend fun startUtfylling(
        rapporteringId: Long,
        ident: String,
        token: String,
    ) {
        hentRapporteringsperioder(ident, token)
            ?.firstOrNull { it.id == rapporteringId }
            ?.let { lagreEllerOppdaterPeriode(it, ident) }
            ?: throw RuntimeException("Fant ingen gjeldende periode for ident $ident")
    }

    suspend fun hentInnsendteRapporteringsperioder(
        ident: String,
        token: String,
    ): List<Rapporteringsperiode> =
        meldepliktConnector
            .hentInnsendteRapporteringsperioder(ident, token)
            .toRapporteringsperioder()
            .sortedByDescending { it.periode.fraOgMed }
            .take(5)

    fun lagreEllerOppdaterPeriode(
        periode: Rapporteringsperiode,
        ident: String,
    ): Rapporteringsperiode {
        val periodeFraDb = rapporteringRepository.hentRapporteringsperiode(periode.id, ident)
        return if (periodeFraDb == null) {
            rapporteringRepository.lagreRapporteringsperiodeOgDager(periode, ident)
            periode
        } else {
            if (periodeFraDb.status.ordinal <= periode.status.ordinal) {
                rapporteringRepository.oppdaterRapporteringsperiodeFraArena(periode, ident)
                rapporteringRepository.hentRapporteringsperiode(periode.id, ident)
                    ?: throw RuntimeException("Fant ikke rapporteringsperiode, selv om den skal ha blitt lagret")
            }
            periodeFraDb
        }
    }

    fun lagreEllerOppdaterAktiviteter(
        rapporteringId: Long,
        dag: Dag,
    ) {
        val dagId = rapporteringRepository.hentDagId(rapporteringId, dag.dagIndex)
        val eksisterendeAktiviteter = rapporteringRepository.hentAktiviteter(dagId)
        rapporteringRepository.slettAktiviteter(eksisterendeAktiviteter.map { it.id })
        rapporteringRepository.lagreAktiviteter(rapporteringId, dagId, dag)
    }

    fun oppdaterRegistrertArbeidssoker(
        rapporteringId: Long,
        ident: String,
        registrertArbeidssoker: Boolean,
    ) = rapporteringRepository.oppdaterRegistrertArbeidssoker(
        rapporteringId,
        ident,
        registrertArbeidssoker,
    )

    suspend fun korrigerRapporteringsperiode(
        rapporteringId: Long,
        ident: String,
        token: String,
    ): Rapporteringsperiode {
        val originalPeriode =
            hentPeriode(rapporteringId, ident, token)
                ?: throw RuntimeException("Finner ikke original rapporteringsperiode. Kan ikke korrigere.")

        if (!originalPeriode.kanKorrigeres) {
            throw IllegalArgumentException("Rapporteringsperiode med id $rapporteringId kan ikke korrigeres")
        }

        val korrigertId =
            meldepliktConnector
                .hentKorrigeringId(rapporteringId, token)
                .let { PeriodeId(it.toLong()) }

        val korrigertRapporteringsperiode =
            originalPeriode.copy(
                id = korrigertId.id,
                kanKorrigeres = false,
                kanSendes = true,
                status = Korrigert,
            )

        lagreEllerOppdaterPeriode(korrigertRapporteringsperiode, ident)

        return korrigertRapporteringsperiode
    }

    suspend fun sendRapporteringsperiode(
        rapporteringsperiode: Rapporteringsperiode,
        token: String,
        ident: String,
        loginLevel: Int,
    ): InnsendingResponse {
        rapporteringsperiode.takeIf { it.kanSendes }
            ?: throw IllegalArgumentException("Rapporteringsperiode med id ${rapporteringsperiode.id} kan ikke sendes")

        return meldepliktConnector
            .sendinnRapporteringsperiode(rapporteringsperiode.toAdapterRapporteringsperiode(), token)
            .also { response ->
                if (response.status == "OK") {
                    logger.info("Journalføring rapporteringsperiode ${rapporteringsperiode.id}")
                    journalfoeringService.journalfoer(ident, loginLevel, rapporteringsperiode)

                    rapporteringRepository.oppdaterRapporteringStatus(rapporteringsperiode.id, ident, Innsendt)
                    logger.info { "Oppdaterte status for rapporteringsperiode ${rapporteringsperiode.id} til Innsendt" }
                }
            }
    }

    fun slettMellomlagredeRapporteringsperioder() {
        val rapporteringsperioder = rapporteringRepository.hentRapporteringsperioder()

        // Sletter innsendte rapporteringsperioder
        rapporteringsperioder
            .filter { it.status == Innsendt }
            .also { "Sletter ${it.size} innsendte rapporteringsperioder" }
            .forEach { slettRapporteringsperiode(it.id) }

        // Sleter rapporteringsperioder som ikke er sendt inn til siste frist
        rapporteringsperioder
            .filter {
                val sisteFrist =
                    it.periode.tilOgMed
                        .plusDays(2)
                        .plusWeeks(1)
                it.status != Innsendt && sisteFrist.isBefore(LocalDate.now())
            }.also { logger.info { "Sletter ${it.size} rapporteringsperioder som ikke ble sendt inn til siste frist" } }
            .forEach { slettRapporteringsperiode(it.id) }
    }

    private fun slettRapporteringsperiode(periodeId: Long) =
        try {
            rapporteringRepository.slettRaporteringsperiode(periodeId)
        } catch (e: Exception) {
            logger.error(e) { "Klarte ikke å slette rapporteringsperiode med id $periodeId" }
        }
}
