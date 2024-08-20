package no.nav.dagpenger.rapportering.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.server.plugins.BadRequestException
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.connector.toAdapterRapporteringsperiode
import no.nav.dagpenger.rapportering.connector.toAdapterRapporteringsperioder
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Utdanning
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Endret
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Innsendt
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.repository.RapporteringRepository
import no.nav.dagpenger.rapportering.utils.februar
import no.nav.dagpenger.rapportering.utils.januar
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class RapporteringServiceTest {
    private val meldepliktConnector = mockk<MeldepliktConnector>()
    private val rapporteringRepository = mockk<RapporteringRepository>()
    private val journalfoeringService = mockk<JournalfoeringService>()
    private val rapporteringService = RapporteringService(meldepliktConnector, rapporteringRepository, journalfoeringService)

    private val ident = "12345678910"
    private val token = "jwtToken"

    @Test
    fun `harMeldeplikt returnerer det samme som meldepliktConnector returnerer`() {
        // True
        coEvery { meldepliktConnector.harMeldeplikt(ident, token) } returns "true"

        var harMeldeplikt = runBlocking { rapporteringService.harMeldeplikt(ident, token) }

        harMeldeplikt shouldBe "true"

        // False
        coEvery { meldepliktConnector.harMeldeplikt(ident, token) } returns "false"

        harMeldeplikt = runBlocking { rapporteringService.harMeldeplikt(ident, token) }

        harMeldeplikt shouldBe "false"
    }

    @Test
    fun `hent periode henter spesifisert periode som ikke er sendt inn og lagrer denne i databasen hvis den ikke finnes`() {
        coEvery { meldepliktConnector.hentRapporteringsperioder(ident, token) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), ident) } returns null
        coJustRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }

        val gjeldendePeriode = runBlocking { rapporteringService.hentPeriode(2L, ident, token) }

        with(gjeldendePeriode!!) {
            id shouldBe 2
            periode.fraOgMed shouldBe 15.januar
            periode.tilOgMed shouldBe 28.januar
            dager.size shouldBe 14
            dager.first().aktiviteter shouldBe emptyList()
            kanSendesFra shouldBe 27.januar
            kanSendes shouldBe true
            kanEndres shouldBe false
            bruttoBelop shouldBe null
            status shouldBe TilUtfylling
            registrertArbeidssoker shouldBe null
        }
    }

    @Test
    fun `hent periode henter spesifisert periode som er sendt inn`() {
        coEvery { meldepliktConnector.hentRapporteringsperioder(ident, token) } returns null
        coEvery { meldepliktConnector.hentInnsendteRapporteringsperioder(ident, token) } returns
            rapporteringsperiodeListe.map { it.copy(status = Innsendt) }.toAdapterRapporteringsperioder()
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(any()) } returns emptyList()

        val gjeldendePeriode = runBlocking { rapporteringService.hentPeriode(2L, ident, token) }

        with(gjeldendePeriode!!) {
            id shouldBe 2
            periode.fraOgMed shouldBe 15.januar
            periode.tilOgMed shouldBe 28.januar
            dager.size shouldBe 14
            dager.first().aktiviteter shouldBe emptyList()
            kanSendesFra shouldBe 27.januar
            kanSendes shouldBe true
            kanEndres shouldBe false
            bruttoBelop shouldBe null
            status shouldBe Innsendt
            registrertArbeidssoker shouldBe null
        }
    }

    @Test
    fun `hent periode henter spesifisert periode og populerer denne med data fra databasen hvis den finnes`() {
        val rapporteringsperiodeFraDb =
            rapporteringsperiodeListe.first { it.id == 2L }.copy(
                dager =
                    getDager(
                        startDato = 14.januar,
                        aktivitet =
                            Aktivitet(
                                id = UUID.randomUUID(),
                                type = Utdanning,
                                timer = null,
                            ),
                    ),
            )
        coEvery { meldepliktConnector.hentRapporteringsperioder(ident, token) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), ident) } returns rapporteringsperiodeFraDb
        coJustRun { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }

        val gjeldendePeriode = runBlocking { rapporteringService.hentPeriode(2L, ident, token) }

        with(gjeldendePeriode!!) {
            id shouldBe 2
            periode.fraOgMed shouldBe 15.januar
            periode.tilOgMed shouldBe 28.januar
            dager.size shouldBe 14
            dager shouldBe rapporteringsperiodeFraDb.dager
            kanSendesFra shouldBe 27.januar
            kanSendes shouldBe true
            kanEndres shouldBe false
            bruttoBelop shouldBe null
            status shouldBe TilUtfylling
            registrertArbeidssoker shouldBe null
        }
    }

    @Test
    fun `hent alle rapporteringsperioder henter alle rapporteringsperioder og sorterer listen fra eldst til nyest`() {
        coEvery { meldepliktConnector.hentRapporteringsperioder(any(), any()) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), ident) } returns null

        val rapporteringsperioder = runBlocking { rapporteringService.hentAllePerioderSomKanSendes(ident, token)!! }

        rapporteringsperioder[0].id shouldBe 1L
        rapporteringsperioder[1].id shouldBe 2L
        rapporteringsperioder[2].id shouldBe 3L
        rapporteringsperioder.size shouldBe 3
    }

    @Test
    fun `hent alle rapporteringsperioder populeres med data fra databasen hvis perioden finnes`() {
        coEvery { meldepliktConnector.hentRapporteringsperioder(any(), any()) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        coEvery { rapporteringRepository.hentRapporteringsperiode(1L, ident) } returns
            lagRapporteringsperiode(
                id = 1,
                periode = Periode(fraOgMed = 1.januar, tilOgMed = 14.januar),
            ).copy(dager = getDager(startDato = 1.januar, aktivitet = Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null)))
        coEvery { rapporteringRepository.hentRapporteringsperiode(2L, ident) } returns null
        coEvery { rapporteringRepository.hentRapporteringsperiode(3L, ident) } returns null
        coJustRun { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }

        val rapporteringsperioder = runBlocking { rapporteringService.hentAllePerioderSomKanSendes(ident, token)!! }

        coVerify(exactly = 1) { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }

        rapporteringsperioder[0].id shouldBe 1L
        rapporteringsperioder[0]
            .dager
            .first()
            .aktiviteter
            .first()
            .type shouldBe Utdanning
        rapporteringsperioder[1].id shouldBe 2L
        rapporteringsperioder[2].id shouldBe 3L
        rapporteringsperioder.size shouldBe 3
    }

    @Test
    fun `startUtfylling lagrer perioden i databasen`() {
        coEvery { meldepliktConnector.hentRapporteringsperioder(any(), any()) } returns null
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns null

        shouldThrow<RuntimeException> {
            runBlocking { rapporteringService.startUtfylling(1L, ident, token) }
        }
    }

    @Test
    fun `startUtfylling feiler hvis perioden ikke finnes`() {
        coEvery { meldepliktConnector.hentRapporteringsperioder(any(), any()) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns null
        coJustRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }

        runBlocking { rapporteringService.startUtfylling(1L, ident, token) }

        coVerify(exactly = 1) { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }
    }

    @Test
    fun `kan lagre aktivitet på eksisterende rapporteringsperiode`() {
        val aktiviteter = listOf(Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null))
        coEvery { rapporteringRepository.hentDagId(any(), any()) } returns UUID.randomUUID()
        coEvery { rapporteringRepository.hentAktiviteter(any()) } returns aktiviteter
        coJustRun { rapporteringRepository.slettAktiviteter(any()) }
        coJustRun { rapporteringRepository.lagreAktiviteter(any(), any(), any()) }

        runBlocking {
            rapporteringService.lagreEllerOppdaterAktiviteter(
                rapporteringId = 1L,
                dag =
                    Dag(
                        dato = 1.januar,
                        aktiviteter = aktiviteter,
                        dagIndex = 0,
                    ),
            )
        }

        coVerify(exactly = 1) { rapporteringRepository.lagreAktiviteter(any(), any(), any()) }
        coVerify(exactly = 1) { rapporteringRepository.slettAktiviteter(any()) }
        coVerify(exactly = 1) { rapporteringRepository.hentDagId(any(), any()) }
        coVerify(exactly = 1) { rapporteringRepository.hentAktiviteter(any()) }
    }

    @Test
    fun `kan oppdatere om bruker vil fortsette som registrert arbeidssoker`() {
        coJustRun { rapporteringRepository.oppdaterRegistrertArbeidssoker(any(), any(), any()) }

        runBlocking { rapporteringService.oppdaterRegistrertArbeidssoker(1L, "12345678910", true) }

        coVerify(exactly = 1) { rapporteringRepository.oppdaterRegistrertArbeidssoker(1L, "12345678910", true) }
    }

    @Test
    fun `kan endre rapporteringsperiode`() {
        coEvery { meldepliktConnector.hentInnsendteRapporteringsperioder(any(), any()) } returns
            listOf(rapporteringsperiodeListe.first().copy(id = 123L, kanEndres = true).toAdapterRapporteringsperiode())
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns null
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(ident) } returns emptyList()
        coJustRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }

        val response = runBlocking { rapporteringService.startEndring(123L, ident, token) }

        response.id shouldNotBe 123L
        response.status shouldBe Endret
        coVerify(exactly = 1) { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }
    }

    @Test
    fun `kan ikke endre rapporteringsperiode som ikke kan endres`() {
        coEvery { meldepliktConnector.hentInnsendteRapporteringsperioder(any(), any()) } returns
            listOf(rapporteringsperiodeListe.first().copy(id = 123L, kanEndres = false).toAdapterRapporteringsperiode())
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(any()) } returns emptyList()

        shouldThrow<IllegalArgumentException> {
            runBlocking { rapporteringService.startEndring(123L, ident, token) }
        }
    }

    @Test
    fun `kan ikke endre rapporteringsperiode hvis perioden ikke finnes`() {
        coEvery { meldepliktConnector.hentInnsendteRapporteringsperioder(any(), any()) } returns null
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns null

        shouldThrow<RuntimeException> {
            runBlocking { rapporteringService.startEndring(123L, ident, token) }
        }
    }

    @Test
    fun `kan hente innsendte rapporteringsperioder`() {
        coEvery {
            meldepliktConnector.hentInnsendteRapporteringsperioder(any(), any())
        } returns
            rapporteringsperiodeListe
                .map { it.copy(status = Innsendt, kanSendes = false, kanEndres = true) }
                .toAdapterRapporteringsperioder()
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(any()) } returns emptyList()

        val innsendteRapporteringsperioder = runBlocking { rapporteringService.hentInnsendteRapporteringsperioder(ident, token)!! }

        innsendteRapporteringsperioder.size shouldBe 3
        innsendteRapporteringsperioder[0].id shouldBe 3L
        innsendteRapporteringsperioder[1].id shouldBe 2L
        innsendteRapporteringsperioder[2].id shouldBe 1L
    }

    @Test
    fun `liste med innsendte rapporteringsperioder blir populert med manglende perioder fra databasen som har riktig status`() {
        val perioderFraArena =
            rapporteringsperiodeListe
                .map { it.copy(status = Innsendt, kanSendes = false, kanEndres = true) }
                .toAdapterRapporteringsperioder()
        coEvery { meldepliktConnector.hentInnsendteRapporteringsperioder(any(), any()) } returns perioderFraArena
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(any()) } returns
            listOf(
                lagRapporteringsperiode(4, Periode(1.januar, 14.januar), status = Innsendt),
                lagRapporteringsperiode(5, Periode(15.januar, 28.januar)),
            )

        val innsendteRapporteringsperioder = runBlocking { rapporteringService.hentInnsendteRapporteringsperioder(ident, token)!! }

        innsendteRapporteringsperioder.size shouldBe 4
        innsendteRapporteringsperioder[0].id shouldBe 3L
        innsendteRapporteringsperioder[1].id shouldBe 2L
        innsendteRapporteringsperioder[2].id shouldBe 1L
        innsendteRapporteringsperioder[3].id shouldBe 4L
    }

    @Test
    fun `liste med innsendte rapporteringsperioder blir sortert riktig med endret meldekort før originalt meldekort`() {
        val perioderFraArena =
            rapporteringsperiodeListe
                .map { it.copy(status = Innsendt, kanSendes = false, kanEndres = true) }
                .toAdapterRapporteringsperioder() +
                rapporteringsperiodeListe
                    .first()
                    .copy(
                        id = 10,
                        status = Innsendt,
                        kanSendes = false,
                        begrunnelseEndring = "Korrigert",
                    ).toAdapterRapporteringsperiode()
        coEvery { meldepliktConnector.hentInnsendteRapporteringsperioder(any(), any()) } returns perioderFraArena
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(any()) } returns
            listOf(
                lagRapporteringsperiode(4, Periode(1.januar, 14.januar), status = Innsendt),
                lagRapporteringsperiode(5, Periode(15.januar, 28.januar)),
            )

        val innsendteRapporteringsperioder = runBlocking { rapporteringService.hentInnsendteRapporteringsperioder(ident, token)!! }

        println("Perioder: $innsendteRapporteringsperioder")

        innsendteRapporteringsperioder.size shouldBe 5
        innsendteRapporteringsperioder[0].id shouldBe 10L
        innsendteRapporteringsperioder[0].periode shouldBeEqual innsendteRapporteringsperioder[1].periode
        innsendteRapporteringsperioder[1].id shouldBe 3L
        innsendteRapporteringsperioder[2].id shouldBe 2L
        innsendteRapporteringsperioder[3].id shouldBe 1L
        innsendteRapporteringsperioder[4].id shouldBe 4L
    }

    @Test
    fun `kan sende inn rapporteringsperiode`() {
        val rapporteringsperiode = rapporteringsperiodeListe.first()
        coEvery { journalfoeringService.journalfoer(any(), any(), any(), any()) } returns mockk()
        coJustRun { rapporteringRepository.oppdaterRapporteringStatus(any(), any(), any()) }
        coEvery { meldepliktConnector.sendinnRapporteringsperiode(any(), token) } returns
            InnsendingResponse(
                id = rapporteringsperiode.id,
                status = "OK",
                feil = listOf(),
            )

        val innsendingResponse =
            runBlocking {
                rapporteringService.sendRapporteringsperiode(rapporteringsperiode, token, ident, 4)
            }

        innsendingResponse.id shouldBe rapporteringsperiode.id
        innsendingResponse.status shouldBe "OK"

        verify(exactly = 1) {
            runBlocking {
                journalfoeringService.journalfoer(any(), any(), any(), any())
            }
        }
        coVerify(exactly = 1) { rapporteringRepository.oppdaterRapporteringStatus(any(), any(), any()) }
    }

    @Test
    fun `kan ikke sende inn rapporteringsperiode som ikke kan sendes`() {
        shouldThrow<BadRequestException> {
            runBlocking {
                rapporteringService.sendRapporteringsperiode(
                    rapporteringsperiodeListe.first().copy(kanSendes = false),
                    token,
                    ident,
                    4,
                )
            }
        }
    }

    @Test
    fun `kan sende inn endret rapporteringsperiode med begrunnelse`() {
        val endringId = "4"
        val rapporteringsperiode = rapporteringsperiodeListe.first().copy(status = Endret, begrunnelseEndring = "Endring")
        coEvery { journalfoeringService.journalfoer(any(), any(), any(), any()) } returns mockk()
        coJustRun { rapporteringRepository.oppdaterRapporteringStatus(any(), any(), any()) }
        coEvery { meldepliktConnector.hentEndringId(any(), any()) } returns endringId
        coJustRun { rapporteringRepository.slettRaporteringsperiode(any()) }
        coJustRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }
        coEvery { meldepliktConnector.sendinnRapporteringsperiode(any(), token) } returns
            InnsendingResponse(
                id = endringId.toLong(),
                status = "OK",
                feil = listOf(),
            )

        val innsendingResponse =
            runBlocking {
                rapporteringService.sendRapporteringsperiode(rapporteringsperiode, token, ident, 4)
            }

        innsendingResponse.id shouldBe endringId.toLong()
        innsendingResponse.status shouldBe "OK"

        verify(exactly = 1) {
            runBlocking {
                journalfoeringService.journalfoer(any(), any(), any(), any())
            }
        }
        coVerify(exactly = 1) { rapporteringRepository.oppdaterRapporteringStatus(any(), any(), any()) }
    }

    @Test
    fun `kan ikke sende inn endret rapporteringsperiode uten begrunnelse`() {
        shouldThrow<BadRequestException> {
            runBlocking {
                rapporteringService.sendRapporteringsperiode(
                    rapporteringsperiodeListe.first().copy(status = Endret, begrunnelseEndring = null),
                    token,
                    ident,
                    4,
                )
            }
        }
    }

    @Test
    fun `lagreEllerOppdaterPeriode lagrer perioden hvis den ikke finnes i databasen`() {
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns null
        coJustRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }

        runBlocking { rapporteringService.lagreEllerOppdaterPeriode(rapporteringsperiodeListe.first(), ident) }

        coVerify(exactly = 1) { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }
    }

    @Test
    fun `lagreEllerOppdaterPeriode oppdaterer perioden hvis den finnes i databasen fra før`() {
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns rapporteringsperiodeListe.first()
        coJustRun { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }

        runBlocking { rapporteringService.lagreEllerOppdaterPeriode(rapporteringsperiodeListe.first(), ident) }

        coVerify(exactly = 1) { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }
    }

    @Test
    fun `lagreEllerOppdaterPeriode oppdaterer ikke perioden hvis den finnes i databasen fra før med en høyere status`() {
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns
            rapporteringsperiodeListe.first().copy(status = Innsendt)

        runBlocking { rapporteringService.lagreEllerOppdaterPeriode(rapporteringsperiodeListe.first(), ident) }

        coVerify(exactly = 0) { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }
    }

    @Test
    fun `kan slette mellomlagrede rapporteringsperioder som er sendt inn`() {
        coEvery { rapporteringRepository.hentAlleLagredeRapporteringsperioder() } returns
            listOf(rapporteringsperiodeListe.first().copy(status = Innsendt))
        coJustRun { rapporteringRepository.slettRaporteringsperiode(any()) }

        runBlocking { rapporteringService.slettMellomlagredeRapporteringsperioder() }

        coVerify(exactly = 1) { rapporteringRepository.slettRaporteringsperiode(3) }
    }

    @Test
    fun `kan slette mellomlagrede rapporteringsperioder som er ikke er sendt inn innen siste frist`() {
        coEvery { rapporteringRepository.hentAlleLagredeRapporteringsperioder() } returns rapporteringsperiodeListe
        coJustRun { rapporteringRepository.slettRaporteringsperiode(any()) }

        runBlocking { rapporteringService.slettMellomlagredeRapporteringsperioder() }

        coVerify(exactly = 3) { rapporteringRepository.slettRaporteringsperiode(any()) }
    }

    @Test
    fun `sletter ikke mellomlagrede rapporteringsperioder som er fortsatt skal være mellomlagret`() {
        coEvery { rapporteringRepository.hentAlleLagredeRapporteringsperioder() } returns fremtidigeRaporteringsperioder

        runBlocking { rapporteringService.slettMellomlagredeRapporteringsperioder() }

        coVerify(exactly = 0) { rapporteringRepository.slettRaporteringsperiode(any()) }
    }
}

val rapporteringsperiodeListe =
    listOf(
        lagRapporteringsperiode(
            id = 3,
            periode = Periode(fraOgMed = 29.januar, tilOgMed = 11.februar),
        ),
        lagRapporteringsperiode(
            id = 1,
            periode = Periode(fraOgMed = 1.januar, tilOgMed = 14.januar),
        ),
        lagRapporteringsperiode(
            id = 2,
            periode = Periode(fraOgMed = 15.januar, tilOgMed = 28.januar),
        ),
    )

val fremtidigeRaporteringsperioder =
    listOf(
        lagRapporteringsperiode(
            id = 1,
            periode =
                Periode(
                    fraOgMed = LocalDate.now().minusWeeks(3).plusDays(1),
                    tilOgMed = LocalDate.now().minusWeeks(1),
                ),
        ),
        lagRapporteringsperiode(
            id = 2,
            periode =
                Periode(
                    fraOgMed = LocalDate.now().minusWeeks(2),
                    tilOgMed = LocalDate.now().minusDays(1),
                ),
        ),
        lagRapporteringsperiode(
            id = 3,
            periode =
                Periode(
                    fraOgMed = LocalDate.now(),
                    tilOgMed = LocalDate.now().plusWeeks(2).minusDays(1),
                ),
        ),
    )

fun lagRapporteringsperiode(
    id: Long,
    periode: Periode,
    status: RapporteringsperiodeStatus = TilUtfylling,
) = Rapporteringsperiode(
    id = id,
    periode = periode,
    dager = getDager(startDato = periode.fraOgMed),
    kanSendesFra = periode.tilOgMed.minusDays(1),
    kanSendes = true,
    kanEndres = false,
    bruttoBelop = null,
    status = status,
    registrertArbeidssoker = null,
    begrunnelseEndring = null,
)

private fun getDager(
    startDato: LocalDate = 1.januar,
    aktivitet: Aktivitet? = null,
): List<Dag> =
    (0..13)
        .map { i ->
            Dag(
                dato = startDato.plusDays(i.toLong()),
                aktiviteter = aktivitet?.let { listOf(it) } ?: emptyList(),
                dagIndex = i,
            )
        }
