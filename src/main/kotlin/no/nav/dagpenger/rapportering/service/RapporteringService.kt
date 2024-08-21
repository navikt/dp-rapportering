package no.nav.dagpenger.rapportering.service

import io.ktor.server.plugins.BadRequestException
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.connector.toAdapterRapporteringsperiode
import no.nav.dagpenger.rapportering.connector.toRapporteringsperioder
import no.nav.dagpenger.rapportering.metrics.RapporteringsperiodeMetrikker
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Endret
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Ferdig
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Innsendt
import no.nav.dagpenger.rapportering.repository.RapporteringRepository
import java.time.LocalDate
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

class RapporteringService(
    private val meldepliktConnector: MeldepliktConnector,
    private val rapporteringRepository: RapporteringRepository,
    private val journalfoeringService: JournalfoeringService,
) {
    suspend fun harMeldeplikt(
        ident: String,
        token: String,
    ): String = meldepliktConnector.harMeldeplikt(ident, token)

    suspend fun hentPeriode(
        rapporteringId: Long,
        ident: String,
        token: String,
    ): Rapporteringsperiode? =
        hentRapporteringsperioder(ident, token)
            ?.firstOrNull { it.id == rapporteringId }
            ?.let { lagreEllerOppdaterPeriode(it, ident) }
            ?: hentInnsendteRapporteringsperioder(ident, token)
                ?.firstOrNull { it.id == rapporteringId }
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
            ?: throw RuntimeException("Fant ingen ikke periode med id $rapporteringId for ident $ident")
    }

    suspend fun startEndring(
        rapporteringId: Long,
        ident: String,
        token: String,
    ): Rapporteringsperiode =
        hentInnsendteRapporteringsperioder(ident, token)
            ?.firstOrNull { it.id == rapporteringId }
            .run { this ?: throw RuntimeException("Fant ingen innsendt periode med id $rapporteringId for ident $ident") }
            .takeIf { it.kanEndres }
            .run { this ?: throw IllegalArgumentException("Perioden med id $rapporteringId kan ikke endres") }
            .let { originalPeriode ->
                lagreEllerOppdaterPeriode(
                    originalPeriode.copy(
                        id = originalPeriode.id + Random.nextLong(),
                        kanEndres = false,
                        kanSendes = true,
                        status = Endret,
                    ),
                    ident,
                )
            }

    suspend fun hentInnsendteRapporteringsperioder(
        ident: String,
        token: String,
    ): List<Rapporteringsperiode>? =
        meldepliktConnector
            .hentInnsendteRapporteringsperioder(ident, token)
            .toRapporteringsperioder()
            .populerMedPerioderFraDatabase(ident)
            .sortedWith(
                compareByDescending<Rapporteringsperiode> { it.periode.fraOgMed }
                    .thenByDescending { it.begrunnelseEndring != null },
            ).take(5)
            .ifEmpty { null }

    private suspend fun List<Rapporteringsperiode>.populerMedPerioderFraDatabase(ident: String): List<Rapporteringsperiode> {
        val innsendteRapporteringsperioder = this.toMutableList()
        rapporteringRepository
            .hentLagredeRapporteringsperioder(ident)
            .filter { it.status == Innsendt || it.status == Ferdig }
            .forEach { periodeFraDb ->
                if (innsendteRapporteringsperioder.none { it.id == periodeFraDb.id }) {
                    innsendteRapporteringsperioder.add(periodeFraDb)
                }
            }
        return innsendteRapporteringsperioder
    }

    suspend fun lagreEllerOppdaterPeriode(
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

    suspend fun lagreEllerOppdaterAktiviteter(
        rapporteringId: Long,
        dag: Dag,
    ) {
        val dagId = rapporteringRepository.hentDagId(rapporteringId, dag.dagIndex)
        val eksisterendeAktiviteter = rapporteringRepository.hentAktiviteter(dagId)
        rapporteringRepository.slettAktiviteter(eksisterendeAktiviteter.map { it.id })
        rapporteringRepository.lagreAktiviteter(rapporteringId, dagId, dag)
    }

    suspend fun oppdaterRegistrertArbeidssoker(
        rapporteringId: Long,
        ident: String,
        registrertArbeidssoker: Boolean,
    ) = rapporteringRepository.oppdaterRegistrertArbeidssoker(
        rapporteringId,
        ident,
        registrertArbeidssoker,
    )

    suspend fun oppdaterBegrunnelse(
        rapporteringId: Long,
        ident: String,
        begrunnelse: String,
    ) = rapporteringRepository.oppdaterBegrunnelse(rapporteringId, ident, begrunnelse)

    suspend fun sendRapporteringsperiode(
        rapporteringsperiode: Rapporteringsperiode,
        token: String,
        ident: String,
        loginLevel: Int,
    ): InnsendingResponse {
        rapporteringsperiode.takeIf { it.kanSendes }
            ?: throw BadRequestException("Rapporteringsperiode med id ${rapporteringsperiode.id} kan ikke sendes")

        var periodeTilInnsending = rapporteringsperiode

        if (rapporteringsperiode.status == Endret) {
            if (rapporteringsperiode.begrunnelseEndring.isNullOrBlank()) {
                throw BadRequestException(
                    "Endret rapporteringsperiode med id ${rapporteringsperiode.id} kan ikke sendes. Begrunnelse for endring må oppgis",
                )
            } else {
                val endringId =
                    meldepliktConnector
                        .hentEndringId(rapporteringsperiode.id, token)
                        .toLong()

                periodeTilInnsending = rapporteringsperiode.copy(id = endringId)
                rapporteringRepository.slettRaporteringsperiode(rapporteringsperiode.id)
                rapporteringRepository.lagreRapporteringsperiodeOgDager(periodeTilInnsending, ident)
            }
        }

        return meldepliktConnector
            .sendinnRapporteringsperiode(periodeTilInnsending.toAdapterRapporteringsperiode(), token)
            .also { response ->
                if (response.status == "OK") {
                    logger.info("Journalføring rapporteringsperiode ${periodeTilInnsending.id}")
                    journalfoeringService.journalfoer(ident, loginLevel, token, periodeTilInnsending)

                    rapporteringRepository.oppdaterRapporteringStatus(periodeTilInnsending.id, ident, Innsendt)
                    logger.info { "Oppdaterte status for rapporteringsperiode ${periodeTilInnsending.id} til Innsendt" }
                } else {
                    logger.error { "Feil ved innsending av rapporteringsperiode ${periodeTilInnsending.id}: $response" }
                    throw RuntimeException("Feil ved innsending av rapporteringsperiode ${periodeTilInnsending.id}")
                }
            }
    }

    suspend fun slettMellomlagredeRapporteringsperioder(): Int {
        val rapporteringsperioder = rapporteringRepository.hentAlleLagredeRapporteringsperioder()

        var innsendtePerioder = 0
        var foreldredePerioder = 0

        // Sletter innsendte rapporteringsperioder
        rapporteringsperioder
            .filter { it.status == Innsendt }
            .also {
                logger.info { "Sletter ${it.size} innsendte rapporteringsperioder" }
                innsendtePerioder = it.size
            }.forEach { slettRapporteringsperiode(it.id) }

        // Sletter rapporteringsperioder som ikke er sendt inn til siste frist
        rapporteringsperioder
            .filter {
                val sisteFrist =
                    it.periode.tilOgMed
                        .plusDays(2)
                        .plusWeeks(1)
                it.status != Innsendt && sisteFrist.isBefore(LocalDate.now())
            }.also {
                logger.info { "Sletter ${it.size} rapporteringsperioder som ikke ble sendt inn til siste frist" }
                foreldredePerioder = it.size
            }.forEach { slettRapporteringsperiode(it.id) }

        return innsendtePerioder + foreldredePerioder
    }

    private suspend fun slettRapporteringsperiode(periodeId: Long) =
        try {
            rapporteringRepository.slettRaporteringsperiode(periodeId)
        } catch (e: Exception) {
            logger.error(e) { "Klarte ikke å slette rapporteringsperiode med id $periodeId" }
        }
}
