package no.nav.dagpenger.rapportering.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.Headers
import io.ktor.server.plugins.BadRequestException
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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
import no.nav.dagpenger.rapportering.model.Person
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Endret
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Ferdig
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Innsendt
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Midlertidig
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.repository.InnsendingtidspunktRepository
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
    private val innsendingtidspunktRepository = mockk<InnsendingtidspunktRepository>()
    private val rapporteringService =
        RapporteringService(
            meldepliktConnector,
            rapporteringRepository,
            innsendingtidspunktRepository,
            journalfoeringService,
        )

    private val ident = "12345678910"
    private val token = "jwtToken"
    private val loginLevel = 4
    private val headers = Headers.Empty

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
        coEvery { innsendingtidspunktRepository.hentInnsendingtidspunkt(any()) } returns null

        val gjeldendePeriode = runBlocking { rapporteringService.hentPeriode(2L, ident, token, hentOriginal = true) }

        with(gjeldendePeriode!!) {
            id shouldBe 2
            periode.fraOgMed shouldBe 15.januar
            periode.tilOgMed shouldBe 28.januar
            dager.size shouldBe 14
            dager.first().aktiviteter shouldBe emptyList()
            kanSendesFra shouldBe 27.januar
            sisteFristForTrekk shouldBe 5.februar
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

        val gjeldendePeriode = runBlocking { rapporteringService.hentPeriode(2L, ident, token, hentOriginal = true) }

        with(gjeldendePeriode!!) {
            id shouldBe 2
            periode.fraOgMed shouldBe 15.januar
            periode.tilOgMed shouldBe 28.januar
            dager.size shouldBe 14
            dager.first().aktiviteter shouldBe emptyList()
            kanSendesFra shouldBe 27.januar
            sisteFristForTrekk shouldBe 5.februar
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
        coEvery { innsendingtidspunktRepository.hentInnsendingtidspunkt(any()) } returns null

        val gjeldendePeriode = runBlocking { rapporteringService.hentPeriode(2L, ident, token, hentOriginal = true) }

        with(gjeldendePeriode!!) {
            id shouldBe 2
            periode.fraOgMed shouldBe 15.januar
            periode.tilOgMed shouldBe 28.januar
            dager.size shouldBe 14
            dager shouldBe rapporteringsperiodeFraDb.dager
            kanSendesFra shouldBe 27.januar
            sisteFristForTrekk shouldBe 5.februar
            kanSendes shouldBe true
            kanEndres shouldBe false
            bruttoBelop shouldBe null
            status shouldBe TilUtfylling
            registrertArbeidssoker shouldBe null
        }
    }

    @Test
    fun `hent periode justerer når perioden kan sendes inn hvis innsendingstidspunktet skal justeres`() {
        coEvery { meldepliktConnector.hentRapporteringsperioder(ident, token) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), ident) } returns null
        coJustRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }
        coEvery { innsendingtidspunktRepository.hentInnsendingtidspunkt(any()) } returns null
        coEvery { innsendingtidspunktRepository.hentInnsendingtidspunkt("201803") } returns -2

        val gjeldendePeriode = runBlocking { rapporteringService.hentPeriode(2L, ident, token, hentOriginal = true) }

        with(gjeldendePeriode!!) {
            id shouldBe 2
            periode.fraOgMed shouldBe 15.januar
            periode.tilOgMed shouldBe 28.januar
            dager.size shouldBe 14
            dager.first().aktiviteter shouldBe emptyList()
            kanSendesFra shouldBe 26.januar
            sisteFristForTrekk shouldBe 5.februar
            kanSendes shouldBe true
            kanEndres shouldBe false
            bruttoBelop shouldBe null
            status shouldBe TilUtfylling
            registrertArbeidssoker shouldBe null
        }
    }

    @Test
    fun `hent periode henter spesifisert periode kun fra databasen hvis hentOriginal er false`() {
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

        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), ident) } returns rapporteringsperiodeFraDb
        coEvery { innsendingtidspunktRepository.hentInnsendingtidspunkt(any()) } returns null

        val gjeldendePeriode = runBlocking { rapporteringService.hentPeriode(2L, ident, token, hentOriginal = false) }

        with(gjeldendePeriode!!) {
            id shouldBe 2
            periode.fraOgMed shouldBe 15.januar
            periode.tilOgMed shouldBe 28.januar
            dager.size shouldBe 14
            dager shouldBe rapporteringsperiodeFraDb.dager
            kanSendesFra shouldBe 27.januar
            sisteFristForTrekk shouldBe 5.februar
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
        coEvery { innsendingtidspunktRepository.hentInnsendingtidspunkt(any()) } returns null

        val rapporteringsperioder = runBlocking { rapporteringService.hentOgOppdaterRapporteringsperioder(ident, token)!! }

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
        coEvery { innsendingtidspunktRepository.hentInnsendingtidspunkt(any()) } returns null

        val rapporteringsperioder = runBlocking { rapporteringService.hentOgOppdaterRapporteringsperioder(ident, token)!! }

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
        coEvery { innsendingtidspunktRepository.hentInnsendingtidspunkt(any()) } returns null

        runBlocking { rapporteringService.startUtfylling(1L, ident, token) }

        coVerify(exactly = 1) { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }
    }

    @Test
    fun `kan lagre aktivitet på eksisterende rapporteringsperiode`() {
        val aktiviteter = listOf(Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null))
        coEvery { rapporteringRepository.hentDagId(any(), any()) } returns UUID.randomUUID()
        coJustRun { rapporteringRepository.slettOgLagreAktiviteter(any(), any(), any()) }

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

        coVerify(exactly = 1) { rapporteringRepository.slettOgLagreAktiviteter(any(), any(), any()) }
        coVerify(exactly = 1) { rapporteringRepository.hentDagId(any(), any()) }
    }

    @Test
    fun `kan oppdatere om bruker vil fortsette som registrert arbeidssoker`() {
        coJustRun { rapporteringRepository.oppdaterRegistrertArbeidssoker(any(), any(), any()) }

        runBlocking { rapporteringService.oppdaterRegistrertArbeidssoker(1L, "12345678910", true) }

        coVerify(exactly = 1) { rapporteringRepository.oppdaterRegistrertArbeidssoker(1L, "12345678910", true) }
    }

    @Test
    fun `kan oppdatere begrunnelse`() {
        coJustRun { rapporteringRepository.oppdaterBegrunnelse(any(), any(), any()) }

        runBlocking { rapporteringService.oppdaterBegrunnelse(1L, "12345678910", "Begrunnelse") }

        coVerify(exactly = 1) { rapporteringRepository.oppdaterBegrunnelse(1L, "12345678910", "Begrunnelse") }
    }

    @Test
    fun `kan oppdatere rapporteringstype`() {
        coJustRun { rapporteringRepository.oppdaterRapporteringstype(any(), any(), any()) }

        runBlocking { rapporteringService.oppdaterRapporteringstype(1L, "12345678910", "harIngenAktivitet") }

        coVerify(exactly = 1) { rapporteringRepository.oppdaterRapporteringstype(1L, "12345678910", "harIngenAktivitet") }
    }

    @Test
    fun `kan endre rapporteringsperiode`() {
        coEvery { meldepliktConnector.hentInnsendteRapporteringsperioder(any(), any()) } returns
            listOf(rapporteringsperiodeListe.first().copy(id = 123L, kanEndres = true).toAdapterRapporteringsperiode())
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns null
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(ident) } returns emptyList()
        coJustRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }
        coEvery { rapporteringRepository.finnesRapporteringsperiode(any(), any()) } returns true andThen true andThen false

        val response = runBlocking { rapporteringService.startEndring(123L, ident, token) }

        response.id shouldNotBe 123L
        response.status shouldBe TilUtfylling
        response.originalId shouldBe 123L
        coVerify(exactly = 2) { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }
        coVerify(exactly = 3) { rapporteringRepository.finnesRapporteringsperiode(any(), any()) }
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
    fun `liste med innsendte rapporteringsperioder blir populert med siste versjoner av perioder fra databasen`() {
        val perioderFraArena =
            rapporteringsperiodeListe
                .map { it.copy(status = Ferdig, kanSendes = false, kanEndres = true) }
                .toAdapterRapporteringsperioder()
        coEvery { meldepliktConnector.hentInnsendteRapporteringsperioder(any(), any()) } returns perioderFraArena
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(any()) } returns
            listOf(
                lagRapporteringsperiode(1, Periode(1.januar, 14.januar), status = Ferdig, registrertArbeidssoker = true),
                lagRapporteringsperiode(2, Periode(15.januar, 28.januar), status = Innsendt, registrertArbeidssoker = true),
            )

        val innsendteRapporteringsperioder = runBlocking { rapporteringService.hentInnsendteRapporteringsperioder(ident, token)!! }

        // Perioden med ID = 3 finnes ikke i databasen og skal ikke endres
        // Perioden med ID = 2 har lavere status (Innsendt) i databasen enn i Arena (Ferdig) og skal ikke endres
        // Perioden med ID = 1 har samme status i databasen og Arena og skal populeres fra databasen
        innsendteRapporteringsperioder.size shouldBe 3
        innsendteRapporteringsperioder[0].id shouldBe 3L
        innsendteRapporteringsperioder[0].registrertArbeidssoker shouldBe null
        innsendteRapporteringsperioder[1].id shouldBe 2L
        innsendteRapporteringsperioder[1].registrertArbeidssoker shouldBe null
        innsendteRapporteringsperioder[2].id shouldBe 1L
        innsendteRapporteringsperioder[2].registrertArbeidssoker shouldBe true
    }

    @Test
    fun `liste med innsendte rapporteringsperioder blir sortert riktig med endret meldekort før originalt meldekort`() {
        val perioderFraArena =
            rapporteringsperiodeListe
                .map {
                    it.copy(
                        status =
                            if (it.id == 3L) {
                                Endret
                            } else {
                                Innsendt
                            },
                        kanSendes = false,
                        kanEndres = true,
                        mottattDato = it.kanSendesFra,
                    )
                }.toAdapterRapporteringsperioder() +
                rapporteringsperiodeListe
                    .first()
                    .copy(
                        id = 10,
                        status = Innsendt,
                        kanSendes = false,
                        begrunnelseEndring = "Korrigert",
                        mottattDato = LocalDate.now(),
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
        val rapporteringsperiode = rapporteringsperiodeListe.first().copy(registrertArbeidssoker = true)
        coEvery { journalfoeringService.journalfoer(any(), any(), any(), any(), any()) } returns mockk()
        coJustRun { rapporteringRepository.oppdaterPeriodeEtterInnsending(any(), any(), any(), any(), any()) }
        coEvery { meldepliktConnector.hentPerson(any(), any()) } returns Person(1L, "TESTESSEN", "TEST", "NO", "EMELD")
        coEvery { meldepliktConnector.sendinnRapporteringsperiode(any(), token) } returns
            InnsendingResponse(
                id = rapporteringsperiode.id,
                status = "OK",
                feil = listOf(),
            )

        val innsendingResponse =
            runBlocking {
                rapporteringService.sendRapporteringsperiode(rapporteringsperiode, token, ident, loginLevel, headers)
            }

        innsendingResponse.id shouldBe rapporteringsperiode.id
        innsendingResponse.status shouldBe "OK"

        verify(exactly = 1) {
            runBlocking {
                journalfoeringService.journalfoer(any(), any(), any(), any(), any())
            }
        }
        coVerify(exactly = 1) { rapporteringRepository.oppdaterPeriodeEtterInnsending(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `kan ikke sende inn rapporteringsperiode som ikke kan sendes`() {
        shouldThrow<BadRequestException> {
            runBlocking {
                rapporteringService.sendRapporteringsperiode(
                    rapporteringsperiodeListe.first().copy(kanSendes = false),
                    token,
                    ident,
                    loginLevel,
                    headers,
                )
            }
        }
    }

    @Test
    fun `kan sende inn endret rapporteringsperiode med begrunnelse`() {
        val endringId = "4"
        val originalPeriode = rapporteringsperiodeListe.first()
        val rapporteringsperiode =
            originalPeriode.copy(
                status = TilUtfylling,
                begrunnelseEndring = "Endring",
                originalId = originalPeriode.id,
                registrertArbeidssoker = true,
            )
        coEvery { journalfoeringService.journalfoer(any(), any(), any(), any(), any()) } returns mockk()
        coJustRun { rapporteringRepository.oppdaterPeriodeEtterInnsending(any(), any(), any(), any(), any()) }
        coJustRun { rapporteringRepository.oppdaterPeriodeEtterInnsending(any(), any(), any(), any(), any(), false) }
        coEvery { meldepliktConnector.hentEndringId(any(), any()) } returns endringId
        coEvery { meldepliktConnector.hentPerson(any(), any()) } returns Person(1L, "TESTESSEN", "TEST", "NO", "EMELD")
        coJustRun { rapporteringRepository.slettRaporteringsperiode(any()) }
        val periode = slot<Rapporteringsperiode>()
        coJustRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(capture(periode), ident) }
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(any()) } returns emptyList()
        coEvery { meldepliktConnector.hentInnsendteRapporteringsperioder(any(), any()) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        coEvery { meldepliktConnector.sendinnRapporteringsperiode(any(), token) } returns
            InnsendingResponse(
                id = endringId.toLong(),
                status = "OK",
                feil = listOf(),
            )

        val innsendingResponse =
            runBlocking {
                rapporteringService.sendRapporteringsperiode(rapporteringsperiode, token, ident, loginLevel, headers)
            }

        innsendingResponse.id shouldBe endringId.toLong()
        innsendingResponse.status shouldBe "OK"

        verify(exactly = 1) {
            runBlocking {
                journalfoeringService.journalfoer(any(), any(), any(), any(), any())
            }
        }
        coVerify(exactly = 1) {
            rapporteringRepository
                .oppdaterPeriodeEtterInnsending(rapporteringsperiode.id, ident, false, false, Midlertidig)
        }
        coVerify(exactly = 1) {
            rapporteringRepository
                .oppdaterPeriodeEtterInnsending(endringId.toLong(), ident, false, false, Innsendt)
        }
        coVerify(exactly = 1) {
            rapporteringRepository
                .oppdaterPeriodeEtterInnsending(originalPeriode.id, ident, false, false, Innsendt, false)
        }
        coVerify(exactly = 1) { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), ident) }
        periode.captured.id shouldBe endringId.toLong()
        periode.captured.dager.forEachIndexed { dagIndex, dag ->
            dag.dato shouldBe rapporteringsperiode.dager[dagIndex].dato
            dag.dagIndex shouldBe rapporteringsperiode.dager[dagIndex].dagIndex
            dag.aktiviteter.forEachIndexed { index, aktivitet ->
                aktivitet.id shouldNotBe rapporteringsperiode.dager[dagIndex].aktiviteter[index].id
                aktivitet.type shouldBe rapporteringsperiode.dager[dagIndex].aktiviteter[index].type
                aktivitet.timer shouldBe rapporteringsperiode.dager[dagIndex].aktiviteter[index].timer
            }
        }
    }

    @Test
    fun `kan ikke sende inn endret rapporteringsperiode uten begrunnelse`() {
        shouldThrow<BadRequestException> {
            runBlocking {
                rapporteringService.sendRapporteringsperiode(
                    rapporteringsperiodeListe.first().copy(status = TilUtfylling, begrunnelseEndring = null, originalId = 125L),
                    token,
                    ident,
                    loginLevel,
                    headers,
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
    fun `kan slette alle aktiviteter for en periode`() {
        val dagPairList: List<Pair<UUID, Dag>> = getDager().map { UUID.randomUUID() to it }
        coEvery { rapporteringRepository.finnesRapporteringsperiode(any(), any()) } returns true
        coEvery { rapporteringRepository.hentDagerUtenAktivitet(any()) } returns dagPairList
        coJustRun { rapporteringRepository.slettAktiviteter(any()) }

        runBlocking { rapporteringService.resettAktiviteter(1L, ident) }

        coVerify { rapporteringRepository.finnesRapporteringsperiode(1L, ident) }
        coVerify { rapporteringRepository.hentDagerUtenAktivitet(1L) }
        coVerify(exactly = 14) { rapporteringRepository.slettAktiviteter(any()) }
    }

    @Test
    fun `sletter ikke aktiviteter hvis rapporteringsperioden ikke finnes`() {
        coEvery { rapporteringRepository.finnesRapporteringsperiode(any(), any()) } returns false

        shouldThrow<RuntimeException> {
            runBlocking { rapporteringService.resettAktiviteter(1L, ident) }
        }
    }

    @Test
    fun `kan slette mellomlagrede rapporteringsperioder som er sendt inn`() {
        coEvery { rapporteringRepository.hentRapporteringsperiodeIdForInnsendteOgMidlertidigePerioder() } returns
            listOf(rapporteringsperiodeListe.first().id)
        coEvery { rapporteringRepository.hentRapporteringsperiodeIdForPerioderEtterSisteFrist() } returns emptyList()
        coJustRun { rapporteringRepository.slettRaporteringsperiode(any()) }

        val slettedePerioder = runBlocking { rapporteringService.slettMellomlagredeRapporteringsperioder() }

        coVerify(exactly = 1) { rapporteringRepository.slettRaporteringsperiode(3) }
        slettedePerioder shouldBe 1
    }

    @Test
    fun `kan slette mellomlagrede rapporteringsperioder som er ikke er sendt inn innen siste frist`() {
        coEvery { rapporteringRepository.hentRapporteringsperiodeIdForInnsendteOgMidlertidigePerioder() } returns emptyList()
        coEvery { rapporteringRepository.hentRapporteringsperiodeIdForPerioderEtterSisteFrist() } returns
            rapporteringsperiodeListe.map { it.id }
        coJustRun { rapporteringRepository.slettRaporteringsperiode(any()) }

        val slettedePerioder = runBlocking { rapporteringService.slettMellomlagredeRapporteringsperioder() }

        coVerify(exactly = 3) { rapporteringRepository.slettRaporteringsperiode(any()) }
        slettedePerioder shouldBe 3
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
    registrertArbeidssoker: Boolean? = null,
) = Rapporteringsperiode(
    id = id,
    periode = periode,
    dager = getDager(startDato = periode.fraOgMed),
    kanSendesFra = periode.tilOgMed.minusDays(1),
    kanSendes = true,
    kanEndres = false,
    bruttoBelop = null,
    status = status,
    registrertArbeidssoker = registrertArbeidssoker,
    begrunnelseEndring = null,
    originalId = null,
    rapporteringstype = null,
    mottattDato = null,
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
