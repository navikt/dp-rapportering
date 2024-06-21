package no.nav.dagpenger.rapportering.service

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.metrics.RapporteringsperiodeMetrikker
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.PeriodeId
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Innsendt
import no.nav.dagpenger.rapportering.repository.RapporteringRepository
import java.time.LocalDate
import java.util.Calendar
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

class RapporteringService(
    private val meldepliktConnector: MeldepliktConnector,
    private val rapporteringRepository: RapporteringRepository,
    private val journalfoeringService: JournalfoeringService,
) {
    init {
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        val timer = Timer()
        val timerTask: TimerTask =
            object : TimerTask() {
                override fun run() {
                    slettMellomlagredeRapporteringsperioder()
                }
            }

        timer.schedule(timerTask, today.time, TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS))
    }

    suspend fun hentGjeldendePeriode(
        ident: String,
        token: String,
    ): Rapporteringsperiode? =
        meldepliktConnector
            .hentRapporteringsperioder(ident, token)
            ?.minByOrNull { it.periode.fraOgMed }
            ?.let { lagreEllerOppdaterPeriode(it, ident) }

    suspend fun hentPeriode(
        rapporteringId: Long,
        ident: String,
        token: String,
    ): Rapporteringsperiode? =
        meldepliktConnector
            .hentRapporteringsperioder(ident, token)
            ?.firstOrNull { it.id == rapporteringId }
            ?.let { lagreEllerOppdaterPeriode(it, ident) }
            ?: meldepliktConnector
                .hentInnsendteRapporteringsperioder(ident, token)
                .firstOrNull { it.id == rapporteringId }

    suspend fun hentAlleRapporteringsperioder(
        ident: String,
        token: String,
    ): List<Rapporteringsperiode>? =
        meldepliktConnector
            .hentRapporteringsperioder(ident, token)
            ?.sortedBy { it.periode.fraOgMed }
            .also { RapporteringsperiodeMetrikker.hentet.inc() }

    private fun lagreEllerOppdaterPeriode(
        periode: Rapporteringsperiode,
        ident: String,
    ): Rapporteringsperiode =
        if (rapporteringRepository.hentRapporteringsperiode(periode.id, ident) == null) {
            rapporteringRepository.lagreRapporteringsperiodeOgDager(periode, ident)
            periode
        } else {
            rapporteringRepository.oppdaterRapporteringsperiodeFraArena(periode, ident)
            rapporteringRepository.hentRapporteringsperiode(periode.id, ident)
                ?: throw RuntimeException("Fant ikke rapporteringsperiode, selv om den skal ha blitt lagret")
        }

    fun lagreEllerOppdaterAktiviteter(
        rapporteringId: Long,
        dag: Dag,
    ) {
        val dagId = rapporteringRepository.hentDagId(rapporteringId, dag.dagIndex)
        val eksisterendeAktiviteter = rapporteringRepository.hentAktiviteter(dagId)
        rapporteringRepository.slettAktiviteter(eksisterendeAktiviteter.map { it.uuid })
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

    // TODO: Skal det skje noe i databasen når rapporteringsperioden korrigeres?
    suspend fun korrigerMeldekort(
        rapporteringId: Long,
        token: String,
    ): PeriodeId =
        meldepliktConnector
            .hentKorrigeringId(rapporteringId, token)
            .let { PeriodeId(it) }

    suspend fun hentInnsendteRapporteringsperioder(
        ident: String,
        token: String,
    ): List<Rapporteringsperiode> =
        meldepliktConnector
            .hentInnsendteRapporteringsperioder(ident, token)
            .sortedByDescending { it.periode.fraOgMed }

    suspend fun sendRapporteringsperiode(
        rapporteringsperiode: Rapporteringsperiode,
        token: String,
        ident: String,
        loginLevel: Int,
    ): InnsendingResponse =
        meldepliktConnector
            .sendinnRapporteringsperiode(rapporteringsperiode, token)
            .also { response ->
                if (response.status == "OK") {
                    logger.info("Journalføring rapporteringsperiode ${rapporteringsperiode.id}")
                    journalfoeringService.journalfoer(ident, loginLevel, rapporteringsperiode)

                    rapporteringRepository.oppdaterRapporteringStatus(rapporteringsperiode.id, ident, Innsendt)
                    logger.info { "Oppdaterte status for rapporteringsperiode ${rapporteringsperiode.id} til Innsendt" }
                }
            }

    fun slettMellomlagredeRapporteringsperioder() {
        val rapporteringsperioder = rapporteringRepository.hentRapporteringsperioder()

        // Sletter innsendte rapporteringsperioder
        rapporteringsperioder
            .filter { it.status == Innsendt }
            .also { "Sletter ${it.size} innsendte rapporteringsperioder" }
            .forEach { rapporteringRepository.slettRaporteringsperiode(it.id) }

        // Sleter rapporteringsperioder som ikke er sendt inn til siste frist
        rapporteringsperioder
            .filter {
                val sisteFrist =
                    it.periode.tilOgMed
                        .plusDays(2)
                        .plusWeeks(1)
                it.status != Innsendt && sisteFrist.isBefore(LocalDate.now())
            }.also { logger.info { "Sletter ${it.size} rapporteringsperioder som ikke ble sendt inn til siste frist" } }
            .forEach { rapporteringRepository.slettRaporteringsperiode(it.id) }
    }
}
