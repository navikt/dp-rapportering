package no.nav.dagpenger.rapportering.service

import io.ktor.http.Headers
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
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Feilet
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Ferdig
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Innsendt
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.repository.InnsendingtidspunktRepository
import no.nav.dagpenger.rapportering.repository.RapporteringRepository
import no.nav.dagpenger.rapportering.utils.PeriodeUtils.finnKanSendesFra
import no.nav.dagpenger.rapportering.utils.PeriodeUtils.finnPeriodeKode
import no.nav.dagpenger.rapportering.utils.PeriodeUtils.kanSendesInn
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random
import kotlin.random.nextLong

private val logger = KotlinLogging.logger {}

class RapporteringService(
    private val meldepliktConnector: MeldepliktConnector,
    private val rapporteringRepository: RapporteringRepository,
    private val innsendingtidspunktRepository: InnsendingtidspunktRepository,
    private val journalfoeringService: JournalfoeringService,
    private val rapporteringsperiodeMetrikker: RapporteringsperiodeMetrikker,
) {
    suspend fun harMeldeplikt(
        ident: String,
        token: String,
    ): String = meldepliktConnector.harMeldeplikt(ident, token)

    suspend fun hentPeriode(
        rapporteringId: Long,
        ident: String,
        token: String,
        hentOriginal: Boolean,
    ): Rapporteringsperiode? =
        if (hentOriginal) {
            hentRapporteringsperioder(ident, token)
                ?.firstOrNull { it.id == rapporteringId }
                ?.let { lagreEllerOppdaterPeriode(it, ident) }
                ?: hentInnsendteRapporteringsperioder(ident, token)
                    ?.firstOrNull { it.id == rapporteringId }
                ?: rapporteringRepository
                    .hentRapporteringsperiode(rapporteringId, ident)
        } else {
            rapporteringRepository
                .hentRapporteringsperiode(rapporteringId, ident)
                ?.justerInnsendingstidspunkt()
        }

    suspend fun hentOgOppdaterRapporteringsperioder(
        ident: String,
        token: String,
    ): List<Rapporteringsperiode>? =
        hentRapporteringsperioder(ident, token)
            ?.map { periode ->
                if (rapporteringRepository.hentRapporteringsperiode(periode.id, ident) != null) {
                    rapporteringRepository.oppdaterRapporteringsperiodeFraArena(periode, ident)
                    rapporteringRepository.hentRapporteringsperiode(periode.id, ident)
                        ?: throw RuntimeException("Fant ikke rapporteringsperiode, selv om den er lagret")
                } else {
                    periode
                }
            }?.sortedBy { it.periode.fraOgMed }
            .also { rapporteringsperiodeMetrikker.hentet.increment() }

    private suspend fun hentRapporteringsperioder(
        ident: String,
        token: String,
    ): List<Rapporteringsperiode>? =
        meldepliktConnector
            .hentRapporteringsperioder(ident, token)
            ?.toRapporteringsperioder()
            .justerInnsendingstidspunkt()
            ?.filter { periode ->
                // Filtrerer ut perioder som har en høyere status i databasen enn det vi får fra arena
                rapporteringRepository
                    .hentRapporteringsperiode(periode.id, ident)
                    ?.let { periodeFraDb ->
                        periodeFraDb.status.ordinal <= periode.status.ordinal
                    } ?: true
            }

    private suspend fun List<Rapporteringsperiode>?.justerInnsendingstidspunkt(): List<Rapporteringsperiode>? =
        this?.map { it.justerInnsendingstidspunkt() }

    private suspend fun Rapporteringsperiode.justerInnsendingstidspunkt(): Rapporteringsperiode =
        this.let {
            val kanSendesFra =
                finnKanSendesFra(
                    tilOgMed = it.periode.tilOgMed,
                    innsendingtidspunktRepository.hentInnsendingtidspunkt(
                        periodeKode = finnPeriodeKode(fraOgMed = it.periode.fraOgMed),
                    ),
                )
            it.copy(
                kanSendesFra = kanSendesFra,
                kanSendes = kanSendesInn(kanSendesFra, it.status),
            )
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
                // Passer på at original periode ligger i databasen
                lagreEllerOppdaterPeriode(originalPeriode, ident)
                // Lagrer ny endring med midlertidig id
                lagreEllerOppdaterPeriode(
                    originalPeriode.copy(
                        id = lagMidlertidigEndringId(ident),
                        kanEndres = false,
                        kanSendes = true,
                        status = TilUtfylling,
                        dager =
                            originalPeriode.dager.map { dag ->
                                dag.copy(
                                    aktiviteter =
                                        dag.aktiviteter.map { aktivitet ->
                                            aktivitet.copy(id = UUID.randomUUID())
                                        },
                                )
                            },
                        originalId = rapporteringId,
                    ),
                    ident,
                )
            }

    private suspend fun lagMidlertidigEndringId(ident: String): Long {
        while (true) {
            val midlertidigId = Random.nextLong(0L..Long.MAX_VALUE)
            if (!rapporteringRepository.finnesRapporteringsperiode(midlertidigId, ident)) {
                return midlertidigId
            }
        }
    }

    suspend fun hentInnsendteRapporteringsperioder(
        ident: String,
        token: String,
    ): List<Rapporteringsperiode>? =
        meldepliktConnector
            .hentInnsendteRapporteringsperioder(ident, token)
            .toRapporteringsperioder()
            .kobleRapporteringsperioder()
            .populerMedPerioderFraDatabase(ident)
            .hentSisteFemPerioderPlussNåværende()
            .ifEmpty { null }

    private fun List<Rapporteringsperiode>.kobleRapporteringsperioder(): List<Rapporteringsperiode> =
        this
            .groupBy { it.periode.fraOgMed }
            .let { gruppertePerioder ->
                gruppertePerioder.map { (_, perioder) ->
                    if (perioder.size > 1) {
                        val originalPeriode =
                            perioder
                                .filter { it.status == Endret }
                                .minByOrNull {
                                    it.mottattDato ?: throw RuntimeException("Innsendt periode har ikke mottatt dato")
                                } ?: throw RuntimeException("Fant ingen original periode")
                        val endredePerioder =
                            perioder
                                .filterNot { it == originalPeriode }
                                .map { it.copy(originalId = originalPeriode.id) }
                        endredePerioder + originalPeriode
                    } else {
                        perioder
                    }
                }
            }.flatten()

    private suspend fun List<Rapporteringsperiode>.populerMedPerioderFraDatabase(ident: String): List<Rapporteringsperiode> {
        val innsendteRapporteringsperioder = this.toMutableList()
        rapporteringRepository
            .hentLagredeRapporteringsperioder(ident)
            .filter { it.status == Innsendt || it.status == Ferdig || it.status == Endret || it.status == Feilet }
            .forEach { periodeFraDb ->
                if (innsendteRapporteringsperioder.none { it.id == periodeFraDb.id }) {
                    innsendteRapporteringsperioder.add(periodeFraDb)
                }
            }
        return innsendteRapporteringsperioder
    }

    private fun List<Rapporteringsperiode>.hentSisteFemPerioderPlussNåværende(): List<Rapporteringsperiode> {
        val nåværendePeriode = this.filter { it.periode.inneholder(LocalDate.now()) }
        val sisteFemPerioderSortert =
            this
                .filterNot { it in nåværendePeriode }
                .groupBy { it.periode.fraOgMed }
                .toSortedMap(compareByDescending { it })
                .entries
                .take(5)
                .associate { it.toPair() }
                .values
                .flatten()
                .sortedWith(
                    compareByDescending<Rapporteringsperiode> { it.periode.fraOgMed }
                        .thenByDescending { it.mottattDato },
                )
        return nåværendePeriode + sisteFemPerioderSortert
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

    suspend fun resettAktiviteter(
        rapporteringId: Long,
        ident: String,
    ) {
        if (!rapporteringRepository.finnesRapporteringsperiode(rapporteringId, ident)) {
            throw RuntimeException("Fant ingen rapporteringsperiode med id $rapporteringId for ident $ident")
        }
        val dager = rapporteringRepository.hentDagerUtenAktivitet(rapporteringId)
        dager.forEach { (dagId, _) ->
            val aktiviteter = rapporteringRepository.hentAktiviteter(dagId)
            if (aktiviteter.isNotEmpty()) {
                rapporteringRepository.slettAktiviteter(aktiviteter.map { it.id })
            }
        }
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

    suspend fun oppdaterRapporteringstype(
        rapporteringId: Long,
        ident: String,
        rapporteringstype: String,
    ) = rapporteringRepository.oppdaterRapporteringstype(rapporteringId, ident, rapporteringstype)

    suspend fun sendRapporteringsperiode(
        rapporteringsperiode: Rapporteringsperiode,
        token: String,
        ident: String,
        loginLevel: Int,
        headers: Headers,
    ): InnsendingResponse {
        rapporteringsperiode.takeIf { it.kanSendes }
            ?: throw BadRequestException("Rapporteringsperiode med id ${rapporteringsperiode.id} kan ikke sendes")

        var periodeTilInnsending = rapporteringsperiode

        if (rapporteringsperiode.status == TilUtfylling && rapporteringsperiode.originalId != null) {
            if (rapporteringsperiode.begrunnelseEndring.isNullOrBlank()) {
                throw BadRequestException(
                    "Endret rapporteringsperiode med id ${rapporteringsperiode.id} kan ikke sendes. Begrunnelse for endring må oppgis",
                )
            } else {
                val endringId =
                    meldepliktConnector
                        .hentEndringId(rapporteringsperiode.originalId, token)
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

                    val person = meldepliktConnector.hentPerson(ident, token)
                    val navn = person?.fornavn + " " + person?.etternavn

                    journalfoeringService.journalfoer(ident, navn, loginLevel, headers, periodeTilInnsending)

                    rapporteringRepository.oppdaterPeriodeEtterInnsending(
                        rapporteringId = periodeTilInnsending.id,
                        ident = ident,
                        kanEndres = periodeTilInnsending.begrunnelseEndring == null && periodeTilInnsending.originalId == null,
                        kanSendes = false,
                        status = Innsendt,
                    )
                    logger.info { "Oppdaterte rapporteringsperiode ${periodeTilInnsending.id} med status Innsendt" }
                    if (periodeTilInnsending.originalId != null) {
                        rapporteringRepository.oppdaterPeriodeEtterInnsending(
                            rapporteringId = periodeTilInnsending.originalId!!,
                            ident = ident,
                            kanEndres = false,
                            kanSendes = false,
                            status = Innsendt,
                            oppdaterMottattDato = false,
                        )
                        logger.info {
                            "Oppdaterte original rapporteringsperiode ${periodeTilInnsending.originalId} " +
                                "med kanEndres og kanSendes til false"
                        }
                    }
                } else {
                    logger.warn { "Feil ved innsending av rapporteringsperiode ${periodeTilInnsending.id}: $response" }
                    rapporteringsperiodeMetrikker.kontrollFeilet.increment()
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
